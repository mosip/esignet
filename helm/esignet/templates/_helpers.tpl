{{/*
Return the proper  image name
*/}}
{{- define "esignet.image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.image "global" .Values.global) }}
{{- end -}}

{{/*
Return the proper image name (for the init container volume-permissions image)
*/}}
{{- define "esignet.volumePermissions.image" -}}
{{- include "common.images.image" ( dict "imageRoot" .Values.volumePermissions.image "global" .Values.global ) -}}
{{- end -}}

{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "esignet.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.volumePermissions.image) "global" .Values.global) -}}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "esignet.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (printf "%s" (include "common.names.fullname" .)) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Compile all warnings into a single message.
*/}}
{{- define "esignet.validateValues" -}}
{{- $messages := list -}}
{{- $messages := append $messages (include "esignet.validateValues.foo" .) -}}
{{- $messages := append $messages (include "esignet.validateValues.bar" .) -}}
{{- $messages := append $messages (include "esignet.validateValues.customCA" .) -}}
{{- $messages := without $messages "" -}}
{{- $message := join "\n" $messages -}}

{{- if $message -}}
{{- fail $message -}}
{{- end -}}
{{- end -}}

{{/*
Return true when a custom Java truststore should be mounted into the container.
*/}}
{{- define "esignet.truststoreRequired" -}}
{{- or .Values.enable_insecure .Values.customCA.enabled -}}
{{- end -}}

{{/*
Return the image used by the company-internal CA init container.
*/}}
{{- define "esignet.customCAInitImage" -}}
{{- $image := .Values.customCA.initContainerImage -}}
{{- if .Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .Values.global.imageRegistry $image.repository $image.tag -}}
{{- else if $image.registry -}}
{{- printf "%s/%s:%s" $image.registry $image.repository $image.tag -}}
{{- else -}}
{{- printf "%s:%s" $image.repository $image.tag -}}
{{- end -}}
{{- end -}}

{{/*
Validate customCA configuration.
*/}}
{{- define "esignet.validateValues.customCA" -}}
{{- if and .Values.customCA.enabled (and (not .Values.customCA.secretName) (not .Values.customCA.configMapName)) -}}
{{- fail "When customCA.enabled is true, set either customCA.secretName or customCA.configMapName." -}}
{{- end -}}
{{- if and .Values.customCA.enabled .Values.customCA.secretName .Values.customCA.configMapName -}}
{{- fail "When customCA.enabled is true, set either customCA.secretName or customCA.configMapName, not both." -}}
{{- end -}}
{{- if and .Values.enable_insecure .Values.customCA.enabled -}}
{{- fail "enable_insecure and customCA.enabled cannot be used together. Use customCA for company-internal PKI." -}}
{{- end -}}
{{- end -}}

{{/*
Return podAnnotations
*/}}
{{- define "esignet.podAnnotations" -}}
{{- if .Values.podAnnotations }}
{{ include "common.tplvalues.render" (dict "value" .Values.podAnnotations "context" $) }}
{{- end }}
{{- if and .Values.metrics.enabled .Values.metrics.podAnnotations }}
{{ include "common.tplvalues.render" (dict "value" .Values.metrics.podAnnotations "context" $) }}
{{- end }}
{{- end -}}


