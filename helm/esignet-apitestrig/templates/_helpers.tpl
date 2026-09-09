{{/*
Return the proper image name
*/}}
{{- define "apitestrig.image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.image "global" .Values.global) }}
{{- end -}}

{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "apitestrig.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list .Values.image) "global" .Values.global) -}}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "apitestrig.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (printf "%s" (include "common.names.fullname" .)) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Resolve the -c value to pass to run-all.sh: the mounted custom config when
apitestrig.configOverride is set, otherwise the in-image path from
apitestrig.configFile.
*/}}
{{- define "apitestrig.configPath" -}}
{{- if .Values.apitestrig.configOverride -}}
/app/custom-config/config.json
{{- else -}}
{{ .Values.apitestrig.configFile }}
{{- end -}}
{{- end -}}

{{/*
Name of the Secret backing the config.local.json overlay, whether
Helm-managed or user-supplied via existingSecret.
*/}}
{{- define "apitestrig.configLocalSecretName" -}}
{{- if .Values.apitestrig.configLocal.existingSecret -}}
{{ .Values.apitestrig.configLocal.existingSecret }}
{{- else -}}
{{ include "common.names.fullname" . }}-config-local
{{- end -}}
{{- end -}}

{{/*
Name of the Secret backing the conformance plan config files.
*/}}
{{- define "apitestrig.conformancePlanConfigSecretName" -}}
{{- if .Values.apitestrig.conformancePlanConfig.existingSecret -}}
{{ .Values.apitestrig.conformancePlanConfig.existingSecret }}
{{- else -}}
{{ include "common.names.fullname" . }}-conformance-plan
{{- end -}}
{{- end -}}

{{/*
Name of the Secret backing extraEnvVarsSecret.
*/}}
{{- define "apitestrig.envSecretName" -}}
{{ include "common.names.fullname" . }}-env
{{- end -}}

{{/*
Name of the Secret backing S3 report-push credentials.
*/}}
{{- define "apitestrig.s3SecretName" -}}
{{- if .Values.reports.s3.existingSecret -}}
{{ .Values.reports.s3.existingSecret }}
{{- else -}}
{{ include "common.names.fullname" . }}-s3
{{- end -}}
{{- end -}}

{{/*
Name of the PVC backing the reports volume.
*/}}
{{- define "apitestrig.reportsClaimName" -}}
{{- if .Values.reports.persistence.existingClaim -}}
{{ .Values.reports.persistence.existingClaim }}
{{- else -}}
{{ include "common.names.fullname" . }}-reports
{{- end -}}
{{- end -}}

{{/*
The shared pod spec used by both cronjob.yaml and job.yaml, so the two
trigger kinds can never drift out of sync with each other.
*/}}
{{- define "apitestrig.podTemplate" -}}
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
  {{- if .Values.apitestrig.conformanceSuite.enabled }}
  shareProcessNamespace: true
  {{- end }}
  serviceAccountName: {{ include "apitestrig.serviceAccountName" . }}
  {{- include "apitestrig.imagePullSecrets" . | nindent 2 }}
  {{- if .Values.podSecurityContext.enabled }}
  securityContext: {{- omit .Values.podSecurityContext "enabled" | toYaml | nindent 4 }}
  {{- end }}
  {{- if or .Values.hostAliases .Values.apitestrig.conformanceSuite.enabled }}
  hostAliases:
    {{- if .Values.apitestrig.conformanceSuite.enabled }}
    {{/*
    ASSUMED, NOT YET VERIFIED: the httpd image's baked-in nginx.conf proxies
    to hostnames "server"/"mongodb" (Compose's automatic per-service DNS)
    rather than a configurable upstream -- see values.yaml's
    apitestrig.conformanceSuite comment. Test before relying on this.
    */}}
    - ip: 127.0.0.1
      hostnames:
        - server
        - mongodb
    {{- end }}
    {{- if .Values.hostAliases }}
    {{- include "common.tplvalues.render" (dict "value" .Values.hostAliases "context" $) | nindent 4 }}
    {{- end }}
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
  containers:
    - name: apitestrig
      image: {{ include "apitestrig.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      {{- if .Values.containerSecurityContext.enabled }}
      securityContext: {{- omit .Values.containerSecurityContext "enabled" | toYaml | nindent 8 }}
      {{- end }}
      {{- if .Values.reports.s3.enabled }}
      {{/*
      S3 push needs to know when the harness is done (run-all.sh has no S3
      awareness of its own), so wrap it in a shell that drops a completion
      marker on the shared reports volume for the uploader container to
      watch for, then exits with the harness's own exit code.

      NOTE: .Values.command is intentionally NOT honoured here (unlike the
      non-S3 branch below) -- this branch must own the full command line to
      inject the completion-marker logic, so a custom command can't be
      layered in without conflicting with that. .Values.args is inlined
      directly into this single shell string (not rendered as a separate
      argv list via common.tplvalues.render like the non-S3 branch), so an
      args entry containing a Helm template expression renders differently
      here than in that branch -- stick to plain strings in
      .Values.args if you also set reports.s3.enabled.
      */}}
      command: ["/bin/bash", "-c"]
      args:
        - |
          set -o pipefail
          ./run-all.sh -c {{ include "apitestrig.configPath" . | trim | quote }}{{ if .Values.apitestrig.surfaces }} -s {{ .Values.apitestrig.surfaces | quote }}{{ end }}{{ range .Values.args }} {{ . | quote }}{{ end }}
          rc=$?
          touch {{ printf "%s/.rig-complete" .Values.reports.mountPath | quote }}
          exit $rc
      {{- else }}
      {{- if .Values.command }}
      command: {{- include "common.tplvalues.render" (dict "value" .Values.command "context" $) | nindent 8 }}
      {{- end }}
      args:
        - "-c"
        - {{ include "apitestrig.configPath" . | trim | quote }}
        {{- if .Values.apitestrig.surfaces }}
        - "-s"
        - {{ .Values.apitestrig.surfaces | quote }}
        {{- end }}
        {{- if .Values.args }}
        {{- include "common.tplvalues.render" (dict "value" .Values.args "context" $) | nindent 8 }}
        {{- end }}
      {{- end }}
      env:
        - name: REPORT_DIR
          value: {{ .Values.reports.mountPath | quote }}
        {{- if .Values.apitestrig.configLocal.enabled }}
        - name: CONFIG_LOCAL
          value: "/app/secrets/config.local.json"
        {{- end }}
        {{- range $key, $value := .Values.apitestrig.extraEnvVars }}
        {{- if $value }}
        - name: {{ $key }}
          value: {{ $value | quote }}
        {{- end }}
        {{- end }}
        {{- range $key, $value := .Values.apitestrig.extraEnvVarsSecret }}
        {{- if $value }}
        - name: {{ $key }}
          valueFrom:
            secretKeyRef:
              name: {{ include "apitestrig.envSecretName" $ }}
              key: {{ $key }}
        {{- end }}
        {{- end }}
      envFrom:
        {{- range .Values.apitestrig.extraEnvVarsCM }}
        - configMapRef:
            name: {{ . }}
        {{- end }}
        {{- range .Values.apitestrig.extraEnvVarsSecretRefs }}
        - secretRef:
            name: {{ . }}
        {{- end }}
      volumeMounts:
        - name: reports
          mountPath: {{ .Values.reports.mountPath }}
        {{- if .Values.apitestrig.configLocal.enabled }}
        - name: config-local
          mountPath: /app/secrets
          readOnly: true
        {{- end }}
        {{- if .Values.apitestrig.configOverride }}
        - name: custom-config
          mountPath: /app/custom-config
          readOnly: true
        {{- end }}
        {{- if .Values.apitestrig.conformancePlanConfig.enabled }}
        - name: conformance-plan-config
          mountPath: /app/conformance-suite-private
          readOnly: true
        {{- end }}
      resources: {{- toYaml .Values.resources | nindent 8 }}
    {{- if .Values.reports.s3.enabled }}
    - name: report-uploader
      image: {{ include "common.images.image" (dict "imageRoot" .Values.reports.s3.image "global" .Values.global) }}
      imagePullPolicy: {{ .Values.reports.s3.image.pullPolicy }}
      command: ["/bin/sh", "-c"]
      args:
        - |
          set -e
          export HOME=/tmp
          until [ -f {{ printf "%s/.rig-complete" .Values.reports.mountPath | quote }} ]; do
            sleep 5
          done
          [ "$S3_INSECURE" = "true" ] && export MC_INSECURE=true
          # mc alias set puts credentials in this process's own argv, which
          # shareProcessNamespace: true (used when apitestrig.conformanceSuite
          # is also enabled) makes visible to every other container in this
          # pod via /proc/<pid>/cmdline. MC_HOST_target avoids that entirely --
          # NOTE: this assumes S3_ACCESS_KEY/S3_SECRET_KEY contain no
          # "@", ":", "/" or "#" characters; URL-encode them first if they do.
          SCHEME=${S3_ENDPOINT%%://*}
          HOSTPART=${S3_ENDPOINT#*://}
          export MC_HOST_target="$SCHEME://$S3_ACCESS_KEY:$S3_SECRET_KEY@$HOSTPART"
          mc mb --ignore-existing "target/$S3_BUCKET"
          # IST is UTC+5:30. Computed as raw seconds rather than TZ=Asia/Kolkata
          # so this doesn't depend on a timezone database being present in
          # this minimal image -- pure arithmetic on the epoch, then format
          # the shifted value "as UTC" to get the correct IST wall-clock time.
          IST_EPOCH=$(( $(date -u +%s) + 19800 ))
          DEST="target/$S3_BUCKET/$S3_PATH_PREFIX/$(date -u -d @$IST_EPOCH +%Y-%m-%d_%H-%M)"
          mc cp --recursive {{ .Values.reports.mountPath }}/ "$DEST/"
          echo "Uploaded reports to $DEST"
      env:
        - name: S3_ENDPOINT
          value: {{ .Values.reports.s3.endpoint | quote }}
        - name: S3_BUCKET
          value: {{ .Values.reports.s3.bucket | quote }}
        - name: S3_PATH_PREFIX
          value: {{ .Values.reports.s3.pathPrefix | quote }}
        - name: S3_INSECURE
          value: {{ .Values.reports.s3.insecure | quote }}
        - name: S3_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: {{ include "apitestrig.s3SecretName" . }}
              key: {{ .Values.reports.s3.existingSecretAccessKeyKey }}
        - name: S3_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: {{ include "apitestrig.s3SecretName" . }}
              key: {{ .Values.reports.s3.existingSecretSecretKeyKey }}
      volumeMounts:
        - name: reports
          mountPath: {{ .Values.reports.mountPath }}
          readOnly: true
    {{- end }}
    {{- if .Values.apitestrig.conformanceSuite.enabled }}
    {{/*
    Regular containers, not initContainers -- no K8s version requirement,
    but nothing here enforces startup ordering the way native sidecars
    would. Each of these apps is expected to retry its own dependency
    connections on startup (Spring Boot retries Mongo; the harness's own
    SUITE_WAIT_SECONDS already retries reaching the suite through httpd) --
    the same "loose" ordering api-test/docker-compose.yml itself relies on
    via depends_on without healthcheck conditions.

    Listed after apitestrig/report-uploader (rather than before) so that
    tools showing only a pod's "primary" image -- e.g. Rancher's CronJob
    list view -- summarize it as apitestrig, not whichever of these
    happens to be first; this has no effect on how Kubernetes runs them.
    */}}
    - name: conformance-mongodb
      image: {{ include "common.images.image" (dict "imageRoot" .Values.apitestrig.conformanceSuite.mongodb.image "global" .Values.global) }}
      imagePullPolicy: {{ .Values.apitestrig.conformanceSuite.mongodb.image.pullPolicy }}
      resources: {{- toYaml .Values.apitestrig.conformanceSuite.mongodb.resources | nindent 8 }}

    - name: conformance-server
      image: {{ include "common.images.image" (dict "imageRoot" (merge (dict "tag" .Values.apitestrig.conformanceSuite.imageTag) .Values.apitestrig.conformanceSuite.server.image) "global" .Values.global) }}
      imagePullPolicy: {{ .Values.apitestrig.conformanceSuite.server.image.pullPolicy }}
      env:
        - name: BASE_URL
          value: "https://localhost.emobix.co.uk:8443"
        - name: MONGODB_HOST
          value: "127.0.0.1"
        - name: SPRING_PROFILES_ACTIVE
          value: {{ .Values.apitestrig.conformanceSuite.server.springProfile | quote }}
        - name: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITLAB_CLIENTID
          value: "unused"
        - name: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITLAB_CLIENTSECRET
          value: "unused"
        - name: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENTID
          value: "unused"
        - name: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENTSECRET
          value: "unused"
      resources: {{- toYaml .Values.apitestrig.conformanceSuite.server.resources | nindent 8 }}

    - name: conformance-httpd
      image: {{ include "common.images.image" (dict "imageRoot" (merge (dict "tag" .Values.apitestrig.conformanceSuite.imageTag) .Values.apitestrig.conformanceSuite.httpd.image) "global" .Values.global) }}
      imagePullPolicy: {{ .Values.apitestrig.conformanceSuite.httpd.image.pullPolicy }}
      resources: {{- toYaml .Values.apitestrig.conformanceSuite.httpd.resources | nindent 8 }}

    {{/*
    Waits for the same completion marker report-uploader watches for, then
    kills the suite's processes by name (needs shareProcessNamespace: true,
    set above, to see PIDs across containers) so mongodb/server/httpd exit
    and the Job can complete -- they never exit on their own otherwise.
    */}}
    - name: conformance-reaper
      image: {{ include "common.images.image" (dict "imageRoot" .Values.apitestrig.conformanceSuite.reaper.image "global" .Values.global) }}
      imagePullPolicy: {{ .Values.apitestrig.conformanceSuite.reaper.image.pullPolicy }}
      command: ["/bin/sh", "-c"]
      args:
        - |
          until [ -f {{ printf "%s/.rig-complete" .Values.reports.mountPath | quote }} ]; do
            sleep 5
          done
          # Give report-uploader a moment to finish reading the volume first.
          sleep 5
          # Reads /proc/<pid>/comm directly rather than parsing `ps` output --
          # a kernel feature, not a ps feature, so it works regardless of
          # which ps applet variant this busybox build shipped with.
          for pid_dir in /proc/[0-9]*; do
            pid=${pid_dir#/proc/}
            comm=$(cat "$pid_dir/comm" 2>/dev/null) || continue
            for name in {{ .Values.apitestrig.conformanceSuite.reaper.processNames | join " " }}; do
              if [ "$comm" = "$name" ]; then
                kill "$pid" 2>/dev/null
              fi
            done
          done
          exit 0
      volumeMounts:
        - name: reports
          mountPath: {{ .Values.reports.mountPath }}
          readOnly: true
    {{- end }}
  volumes:
    - name: reports
      {{- if .Values.reports.persistence.enabled }}
      persistentVolumeClaim:
        claimName: {{ include "apitestrig.reportsClaimName" . }}
      {{- else }}
      emptyDir: {}
      {{- end }}
    {{- if .Values.apitestrig.configLocal.enabled }}
    - name: config-local
      secret:
        secretName: {{ include "apitestrig.configLocalSecretName" . }}
        defaultMode: 256
        items:
          - key: {{ .Values.apitestrig.configLocal.existingSecretKey }}
            path: config.local.json
    {{- end }}
    {{- if .Values.apitestrig.configOverride }}
    - name: custom-config
      configMap:
        name: {{ include "common.names.fullname" . }}-config
    {{- end }}
    {{- if .Values.apitestrig.conformancePlanConfig.enabled }}
    - name: conformance-plan-config
      secret:
        secretName: {{ include "apitestrig.conformancePlanConfigSecretName" . }}
        defaultMode: 0400
    {{- end }}
{{- end -}}
