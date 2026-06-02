package catalog_test

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"
)

func TestDeclarativeFlowYAMLHasNodes(t *testing.T) {
	path := filepath.Join("..", "..", "data", "repository", "resources", "flows", "flow-declarative-1.yaml")
	data, err := os.ReadFile(path)
	require.NoError(t, err)

	var doc struct {
		ID    string                   `yaml:"id"`
		Nodes []map[string]interface{} `yaml:"nodes"`
	}
	require.NoError(t, yaml.Unmarshal(data, &doc))
	require.Equal(t, "decl-flow-1", doc.ID)
	require.NotEmpty(t, doc.Nodes)
}
