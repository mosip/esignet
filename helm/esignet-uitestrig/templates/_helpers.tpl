{{/*
Return the proper image name
*/}}
{{- define "uitestrig.image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.image "global" .Values.global) }}
{{- end -}}

{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "uitestrig.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list .Values.image) "global" .Values.global) -}}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "uitestrig.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (printf "%s" (include "common.names.fullname" .)) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Name of the ConfigMap rendered from uitestrig.configMap.
*/}}
{{- define "uitestrig.configMapName" -}}
{{ include "common.names.fullname" . }}-config
{{- end -}}

{{/*
Name of the Secret rendered from uitestrig.secret.
*/}}
{{- define "uitestrig.secretName" -}}
{{ include "common.names.fullname" . }}-secret
{{- end -}}

{{/*
Name of the ConfigMap holding the pinned CA cert for enableInsecure.
*/}}
{{- define "uitestrig.caCertConfigMapName" -}}
{{ include "common.names.fullname" . }}-ca-cert
{{- end -}}

{{/*
Name of the Secret backing BrowserStack credentials.
*/}}
{{- define "uitestrig.browserstackSecretName" -}}
{{ include "common.names.fullname" . }}-browserstack
{{- end -}}

{{/*
Name of the PVC backing the reports volume.
*/}}
{{- define "uitestrig.reportsClaimName" -}}
{{- if .Values.reports.persistence.existingClaim -}}
{{ .Values.reports.persistence.existingClaim }}
{{- else -}}
{{ include "common.names.fullname" . }}-reports
{{- end -}}
{{- end -}}

{{/*
The shared pod spec used by both cronjob.yaml and job.yaml.
*/}}
{{- define "uitestrig.podTemplate" -}}
metadata:
  annotations:
    sidecar.istio.io/inject: {{ .Values.istio.sidecarInject | quote }}
    {{- if .Values.podAnnotations }}
    {{- include "common.tplvalues.render" (dict "value" .Values.podAnnotations "context" $) | nindent 4 }}
    {{- end }}
  labels: {{- include "common.labels.standard" . | nindent 4 }}
    {{- if .Values.commonLabels }}
    {{- include "common.tplvalues.render" (dict "value" .Values.commonLabels "context" $) | nindent 4 }}
    {{- end }}
    {{- if .Values.podLabels }}
    {{- include "common.tplvalues.render" (dict "value" .Values.podLabels "context" $) | nindent 4 }}
    {{- end }}
spec:
  restartPolicy: Never
  serviceAccountName: {{ include "uitestrig.serviceAccountName" . }}
  {{- include "uitestrig.imagePullSecrets" . | nindent 2 }}
  {{- if .Values.podSecurityContext.enabled }}
  securityContext: {{- omit .Values.podSecurityContext "enabled" | toYaml | nindent 4 }}
  {{- end }}
  {{- if .Values.hostAliases }}
  hostAliases: {{- include "common.tplvalues.render" (dict "value" .Values.hostAliases "context" $) | nindent 4 }}
  {{- end }}
  {{- if .Values.affinity }}
  affinity: {{- include "common.tplvalues.render" (dict "value" .Values.affinity "context" $) | nindent 4 }}
  {{- end }}
  {{- if .Values.nodeSelector }}
  nodeSelector: {{- include "common.tplvalues.render" (dict "value" .Values.nodeSelector "context" $) | nindent 4 }}
  {{- end }}
  {{- if .Values.tolerations }}
  tolerations: {{- include "common.tplvalues.render" (dict "value" .Values.tolerations "context" $) | nindent 4 }}
  {{- end }}
  initContainers:
    {{/*
    The image bakes in /home/mosip/test-output/SparkReport/ (see Dockerfile)
    for the reporting library's benefit -- mounting a volume at
    reports.mountPath hides that baked-in subdirectory on a fresh
    PVC/emptyDir, so recreate it here before the main container starts.
    Always runs, regardless of enableInsecure.
    */}}
    - name: prepare-report-dirs
      image: {{ include "uitestrig.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      command: ["/bin/sh", "-c"]
      args:
        - mkdir -p {{ .Values.reports.mountPath }}/SparkReport
      volumeMounts:
        - name: reports
          mountPath: {{ .Values.reports.mountPath }}
          subPath: test-output
    {{- if .Values.uitestrig.enableInsecure }}
    {{/*
    Imports a pinned CA certificate into a JVM cacerts truststore, same
    pattern as the old Java apitestrig chart (mosip-functional-tests,
    helm/apitestrig) -- but this image's base is eclipse-temurin, whose
    JAVA_HOME is /opt/java/openjdk (confirmed against Adoptium/Temurin's own
    docs), NOT /usr/local/openjdk-11 like that older chart's image. The base
    image's own built-in USE_SYSTEM_CA_CERTS auto-handling doesn't apply here
    since uitest-esignet's Dockerfile sets its own ENTRYPOINT, bypassing the
    base image's cert-processing entrypoint entirely -- hence doing this by
    hand instead.

    Imports uitestrig.tls.caCert (required below when enableInsecure is
    true) directly, rather than fetching whatever certificate the target
    host presents at runtime via openssl s_client and trusting it
    unconditionally (a TOFU/CWE-295 gap: a network or DNS attacker could
    otherwise get their own certificate trusted). The operator is expected
    to obtain this cert through a trusted channel -- e.g. copying it
    directly from the eSignet ingress/load balancer's own TLS config --
    not by pointing this chart at a live endpoint.

    UNVERIFIED against a live cluster; test before relying on it.
    */}}
    - name: import-cacerts
      image: {{ include "uitestrig.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      command: ["/bin/sh", "-c"]
      args:
        - |
          set -e
          CACERTS_SRC=/opt/java/openjdk/lib/security/cacerts
          cp "$CACERTS_SRC" /tmp/cacerts
          keytool -delete -alias esignet-ca -keystore /tmp/cacerts -storepass changeit >/dev/null 2>&1 || true
          keytool -trustcacerts -keystore /tmp/cacerts -storepass changeit -noprompt \
            -importcert -alias esignet-ca -file /ca-cert/ca.pem
          cp /tmp/cacerts /cacerts/cacerts
      volumeMounts:
        - name: ca-cert
          mountPath: /ca-cert
          readOnly: true
        - name: cacerts
          mountPath: /cacerts
    {{- end }}
  containers:
    - name: uitestrig
      image: {{ include "uitestrig.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      {{- if .Values.containerSecurityContext.enabled }}
      securityContext: {{- omit .Values.containerSecurityContext "enabled" | toYaml | nindent 8 }}
      {{- end }}
      {{/*
      Always wraps the image's own ENTRYPOINT (./entrypoint.sh) rather than
      using it unmodified, to inject uitestrig.configMap/uitestrig.secret's
      hyphenated keys (keycloak-external-url, push-reports-to-s3,
      s3-user-key, ...) into the JVM's process environment -- these are
      mounted as FILES (below), not via envFrom, because envFrom-sourced
      keys containing '-' are silently dropped by the kubelet on
      Kubernetes versions before 1.32 (RelaxedEnvironmentVariableValidation
      is beta in 1.32, GA in 1.34) -- see
      https://kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/.
      Filenames have no such restriction on any version, so each key is
      read from its own file and passed through `env KEY=VALUE ...`, which
      also sidesteps bash's own stricter variable-name rules (bash
      identifiers can't contain '-' either, but `env`'s argument parsing
      -- split on the first '=' -- doesn't go through bash's variable
      syntax at all).
      */}}
      command: ["/bin/bash", "-c"]
      args:
        - |
          set -e
          ENV_ARGS=()
          for dir in /etc/uitestrig-config /etc/uitestrig-secret; do
            if [ -d "$dir" ]; then
              for f in "$dir"/*; do
                [ -f "$f" ] || continue
                key=$(basename "$f")
                val=$(cat "$f")
                ENV_ARGS+=("$key=$val")
              done
            fi
          done
          exec env "${ENV_ARGS[@]}" ./entrypoint.sh
      env:
        {{- range $key, $value := .Values.uitestrig.extraEnvVars }}
        {{- if $value }}
        - name: {{ $key }}
          value: {{ $value | quote }}
        {{- end }}
        {{- end }}
      envFrom:
        {{- if .Values.uitestrig.browserstack.enabled }}
        - secretRef:
            name: {{ include "uitestrig.browserstackSecretName" . }}
        {{- end }}
        {{/*
        External references, not this chart's own ConfigMap/Secret --
        still plain envFrom, so the same K8s 1.32+ caveat above applies if
        whatever you point these at has hyphenated keys too (e.g. a
        keycloak-external-url key on a shared "keycloak-host" ConfigMap).
        */}}
        {{- range .Values.uitestrig.extraEnvVarsCM }}
        - configMapRef:
            name: {{ . }}
        {{- end }}
        {{- range .Values.uitestrig.extraEnvVarsSecretRefs }}
        - secretRef:
            name: {{ . }}
        {{- end }}
      volumeMounts:
        - name: uitestrig-config
          mountPath: /etc/uitestrig-config
          readOnly: true
        - name: uitestrig-secret
          mountPath: /etc/uitestrig-secret
          readOnly: true
        - name: reports
          mountPath: {{ .Values.reports.mountPath }}
          subPath: test-output
        - name: reports
          mountPath: {{ .Values.reports.screenshotsMountPath }}
          subPath: screenshots
        {{- if .Values.uitestrig.enableInsecure }}
        - name: cacerts
          mountPath: /opt/java/openjdk/lib/security/cacerts
          subPath: cacerts
        {{- end }}
      resources: {{- toYaml .Values.resources | nindent 8 }}
  volumes:
    - name: uitestrig-config
      configMap:
        name: {{ include "uitestrig.configMapName" . }}
    - name: uitestrig-secret
      secret:
        secretName: {{ include "uitestrig.secretName" . }}
        defaultMode: 256
    - name: reports
      {{- if .Values.reports.persistence.enabled }}
      persistentVolumeClaim:
        claimName: {{ include "uitestrig.reportsClaimName" . }}
      {{- else }}
      emptyDir: {}
      {{- end }}
    {{- if .Values.uitestrig.enableInsecure }}
    - name: ca-cert
      configMap:
        name: {{ include "uitestrig.caCertConfigMapName" . }}
    - name: cacerts
      emptyDir: {}
    {{- end }}
{{- end -}}
