package log

// Field represents a key-value pair for structured logging.
type Field struct {
	Key   string
	Value interface{}
}

// String creates a Field with a string value.
func String(key, value string) Field {
	return Field{Key: key, Value: value}
}

// Int creates a Field with an integer value.
func Int(key string, value int) Field {
	return Field{Key: key, Value: value}
}

// Bool creates a Field with a boolean value.
func Bool(key string, value bool) Field {
	return Field{Key: key, Value: value}
}

// Any creates a Field with any value.
func Any(key string, value interface{}) Field {
	return Field{Key: key, Value: value}
}

// Error creates a Field with an error value.
func Error(value error) Field {
	return Field{Key: "error", Value: value}
}
