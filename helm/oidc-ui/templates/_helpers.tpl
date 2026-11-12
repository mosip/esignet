{{/*
Return the proper  image name
*/}}
{{- define "oidc-ui.image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.image "global" .Values.global) }}
{{- end -}}

{{/*
Return the proper image name (for the init container volume-permissions image)
*/}}
{{- define "oidc-ui.volumePermissions.image" -}}
{{- include "common.images.image" ( dict "imageRoot" .Values.volumePermissions.image "global" .Values.global ) -}}
{{- end -}}

{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "oidc-ui.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.volumePermissions.image) "global" .Values.global) -}}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "oidc-ui.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (printf "%s-foo" (include "common.names.fullname" .)) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Compile all warnings into a single message.
*/}}
{{- define "oidc-ui.validateValues" -}}
{{- $messages := list -}}
{{- $messages := append $messages (include "oidc-ui.validateValues.foo" .) -}}
{{- $messages := append $messages (include "oidc-ui.validateValues.bar" .) -}}
{{- $messages := without $messages "" -}}
{{- $message := join "\n" $messages -}}

{{- if $message -}}
{{-   printf "\nVALUES VALIDATION:\n%s" $message -}}
{{- end -}}
{{- end -}}

{{/*
Render one podAnnotations source without the reserved checksum annotation.
The generated checksum/nginx-config value in the Deployment is authoritative,
so a user- or metrics-supplied value for that key must never override it.
*/}}
{{- define "oidc-ui.podAnnotations.render" -}}
{{- $annotations := .annotations -}}
{{- if kindIs "map" $annotations }}
{{- $annotations = omit $annotations "checksum/nginx-config" }}
{{- if $annotations }}
{{ include "common.tplvalues.render" (dict "value" $annotations "context" .context) }}
{{- end }}
{{- else }}
{{- $rendered := include "common.tplvalues.render" (dict "value" $annotations "context" .context) }}
{{- $parsed := $rendered | fromYaml }}
{{- if kindIs "map" $parsed }}
{{- $filtered := omit $parsed "checksum/nginx-config" }}
{{- if $filtered }}
{{ $filtered | toYaml }}
{{- end }}
{{- end }}
{{- end }}
{{- end -}}

{{/*
Return podAnnotations
*/}}
{{- define "oidc-ui.podAnnotations" -}}
{{- if .Values.podAnnotations }}
{{ include "oidc-ui.podAnnotations.render" (dict "annotations" .Values.podAnnotations "context" $) }}
{{- end }}
{{- if and .Values.metrics.enabled .Values.metrics.podAnnotations }}
{{ include "oidc-ui.podAnnotations.render" (dict "annotations" .Values.metrics.podAnnotations "context" $) }}
{{- end }}
{{- end -}}
