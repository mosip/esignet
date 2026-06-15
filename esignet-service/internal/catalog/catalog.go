// Package catalog loads declarative host data from YAML resources.
package catalog

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

// User is a host-directory user record.
type User struct {
	ID         string
	EntityType string
	OUID       string
	Username   string
	Password   string
	Attributes json.RawMessage
}

// ClientCertificate holds OAuth client certificate material from declarative YAML.
type ClientCertificate struct {
	Type  string
	Value string
}

// Application is an OAuth application registration from declarative YAML.
type Application struct {
	ID                        string
	Name                      string
	OUID                      string
	ClientID                  string
	RedirectURIs              []string
	GrantTypes                []string
	ResponseTypes             []string
	TokenEndpointAuthMethod   string
	PKCERequired              bool
	PublicClient              bool
	Certificate               *ClientCertificate
	AuthFlowID                string
	RegistrationFlowID        string
	IsRegistrationFlowEnabled bool
	RecoveryFlowID            string
	IsRecoveryFlowEnabled     bool
	ThemeID                   string
	LayoutID                  string
}

// Catalog holds embedder host data loaded from the data directory.
type Catalog struct {
	Users        map[string]*User
	UsersByName  map[string]*User
	Applications map[string]*Application
	Clients      map[string]*Application
}

type userDoc struct {
	ID          string                 `yaml:"id"`
	Type        string                 `yaml:"type"`
	OUID        string                 `yaml:"ou_id"`
	Attributes  map[string]interface{} `yaml:"attributes"`
	Credentials struct {
		Password string `yaml:"password"`
	} `yaml:"credentials"`
}

type appDoc struct {
	ID                        string `yaml:"id"`
	Name                      string `yaml:"name"`
	OUID                      string `yaml:"ou_id"`
	AuthFlowID                string `yaml:"auth_flow_id"`
	RegistrationFlowID        string `yaml:"registration_flow_id"`
	RecoveryFlowID            string `yaml:"recovery_flow_id"`
	IsRegistrationFlowEnabled bool   `yaml:"is_registration_flow_enabled"`
	IsRecoveryFlowEnabled     bool   `yaml:"is_recovery_flow_enabled"`
	ThemeID                   string `yaml:"theme_id"`
	LayoutID                  string `yaml:"layout_id"`
	InboundAuthConfig         []struct {
		Type   string `yaml:"type"`
		Config struct {
			ClientID                string   `yaml:"client_id"`
			RedirectURIs            []string `yaml:"redirect_uris"`
			GrantTypes              []string `yaml:"grant_types"`
			ResponseTypes           []string `yaml:"response_types"`
			TokenEndpointAuthMethod string   `yaml:"token_endpoint_auth_method"`
			PKCERequired            bool     `yaml:"pkce_required"`
			PublicClient            bool     `yaml:"public_client"`
			Certificate             []struct {
				Type  string `yaml:"type"`
				Value string `yaml:"value"`
			} `yaml:"certificate"`
		} `yaml:"config"`
	} `yaml:"inbound_auth_config"`
}

// Load reads users and applications from dataDir/repository/resources.
func Load(dataDir string) (*Catalog, error) {
	resources := filepath.Join(dataDir, "repository", "resources")
	c := &Catalog{
		Users:        make(map[string]*User),
		UsersByName:  make(map[string]*User),
		Applications: make(map[string]*Application),
		Clients:      make(map[string]*Application),
	}
	if err := c.loadUsers(filepath.Join(resources, "users")); err != nil {
		return nil, err
	}
	if err := c.loadApplications(filepath.Join(resources, "applications")); err != nil {
		return nil, err
	}
	if len(c.Users) == 0 {
		return nil, fmt.Errorf("no users found under %s", filepath.Join(resources, "users"))
	}
	if len(c.Applications) == 0 {
		return nil, fmt.Errorf("no applications found under %s", filepath.Join(resources, "applications"))
	}
	return c, nil
}

func (c *Catalog) loadUsers(dir string) error {
	return loadYAMLDir(dir, func(data []byte) error {
		var doc userDoc
		if err := yaml.Unmarshal(data, &doc); err != nil {
			return err
		}
		if doc.ID == "" {
			return fmt.Errorf("user document missing id")
		}
		username, _ := doc.Attributes["username"].(string)
		if username == "" {
			username = doc.ID
		}
		attrs, err := json.Marshal(doc.Attributes)
		if err != nil {
			return err
		}
		user := &User{
			ID:         doc.ID,
			EntityType: doc.Type,
			OUID:       doc.OUID,
			Username:   username,
			Password:   doc.Credentials.Password,
			Attributes: attrs,
		}
		c.Users[user.ID] = user
		c.UsersByName[user.Username] = user
		return nil
	})
}

func (c *Catalog) loadApplications(dir string) error {
	return loadYAMLDir(dir, func(data []byte) error {
		var doc appDoc
		if err := yaml.Unmarshal(data, &doc); err != nil {
			return err
		}
		if doc.ID == "" {
			return fmt.Errorf("application document missing id")
		}
		app := &Application{
			ID:                        doc.ID,
			Name:                      doc.Name,
			OUID:                      doc.OUID,
			AuthFlowID:                doc.AuthFlowID,
			RegistrationFlowID:        doc.RegistrationFlowID,
			RecoveryFlowID:            doc.RecoveryFlowID,
			IsRegistrationFlowEnabled: doc.IsRegistrationFlowEnabled,
			IsRecoveryFlowEnabled:     doc.IsRecoveryFlowEnabled,
			ThemeID:                   doc.ThemeID,
			LayoutID:                  doc.LayoutID,
		}
		for _, inbound := range doc.InboundAuthConfig {
			if !strings.EqualFold(inbound.Type, "oauth2") {
				continue
			}
			cfg := inbound.Config
			app.ClientID = cfg.ClientID
			app.RedirectURIs = append([]string(nil), cfg.RedirectURIs...)
			app.GrantTypes = append([]string(nil), cfg.GrantTypes...)
			app.ResponseTypes = append([]string(nil), cfg.ResponseTypes...)
			app.TokenEndpointAuthMethod = cfg.TokenEndpointAuthMethod
			app.PKCERequired = cfg.PKCERequired
			app.PublicClient = cfg.PublicClient
			if len(cfg.Certificate) > 0 {
				app.Certificate = &ClientCertificate{
					Type:  cfg.Certificate[0].Type,
					Value: cfg.Certificate[0].Value,
				}
			}
		}
		if app.ClientID == "" {
			return fmt.Errorf("application %s has no oauth2 client_id", doc.ID)
		}
		c.Applications[app.ID] = app
		c.Clients[app.ClientID] = app
		return nil
	})
}

func loadYAMLDir(dir string, handle func([]byte) error) error {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return fmt.Errorf("read directory %s: %w", dir, err)
	}
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasSuffix(name, ".yaml") && !strings.HasSuffix(name, ".yml") {
			continue
		}
		data, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			return err
		}
		if err := handle(data); err != nil {
			return fmt.Errorf("%s: %w", name, err)
		}
	}
	return nil
}

// FindUserByUsername returns a user for the given username.
func (c *Catalog) FindUserByUsername(username string) (*User, bool) {
	user, ok := c.UsersByName[username]
	return user, ok
}

// ApplicationByID returns an application by id.
func (c *Catalog) ApplicationByID(id string) (*Application, bool) {
	app, ok := c.Applications[id]
	return app, ok
}

// ApplicationByClientID returns an application by OAuth client id.
func (c *Catalog) ApplicationByClientID(clientID string) (*Application, bool) {
	app, ok := c.Clients[clientID]
	return app, ok
}
