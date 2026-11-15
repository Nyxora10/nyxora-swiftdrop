{{/* Common labels for every resource */}}
{{- define "swiftdrop.labels" -}}
app.kubernetes.io/part-of: swiftdrop
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}
