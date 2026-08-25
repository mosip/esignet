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
Return GOMEMLIMIT for the Go esignet-service binary.
Uses additionalResources.goMemLimit verbatim if explicitly set. Otherwise derives
~90% of resources.limits.memory on every render, so it can never go stale when the
memory limit changes. Only Ki/Mi/Gi/Ti suffixes (or unitless bytes) are supported
for auto-derivation, matching every memory value used across this chart's values
files; anything else requires an explicit additionalResources.goMemLimit override.
Returns "" if neither is set/derivable, meaning GOMEMLIMIT is omitted.
*/}}
{{- define "esignet.goMemLimit" -}}
{{- $limits := (.Values.resources | default dict).limits | default dict -}}
{{- if .Values.additionalResources.goMemLimit -}}
{{- .Values.additionalResources.goMemLimit -}}
{{- else if $limits.memory -}}
{{- $mem := $limits.memory | toString -}}
{{- $num := regexFind "^[0-9]+" $mem -}}
{{- $unit := regexFind "[A-Za-z]+$" $mem -}}
{{- if and $num (or (eq $unit "Ki") (eq $unit "Mi") (eq $unit "Gi") (eq $unit "Ti") (eq $unit "")) -}}
{{- $suffix := "" -}}
{{- if $unit -}}
{{- $suffix = printf "%sB" $unit -}}
{{- end -}}
{{- printf "%d%s" (div (mul (atoi $num) 9) 10) $suffix -}}
{{- else -}}
{{- fail (printf "additionalResources.goMemLimit: cannot auto-derive GOMEMLIMIT from resources.limits.memory %q (unsupported unit %q); set additionalResources.goMemLimit explicitly instead, e.g. \"2025MiB\"" $mem $unit) -}}
{{- end -}}
{{- end -}}
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
{{- $messages := without $messages "" -}}
{{- $message := join "\n" $messages -}}

{{- if $message -}}
{{-   printf "\nVALUES VALIDATION:\n%s" $message -}}
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


