## Steps to obtain OPENAPI yaml on version 3.0.3

Steps to downgrade OpenAPI from version 3.1.0 to 3.0.3:

1. Download openapi.json from http://localhost:9621/openapi.json
2. Go to https://editor.swagger.io/ and import the JSON file. This step will convert the file to YAML.
3. Change the OpenAPI version to 3.0.3
4. Fix the errors (warnings can be corrected but are not necessary):
    - 4.1. Replace all instances of the string ‘- type: “null”’ with ‘- nullable: true’
    - 4.2. Remove “properties” marked with errors.
5. Remove `securitySchemes` entries (other than basic auth). Remove, for example, `OAuth2PasswordBearer`. The entries in the specification can be kept.
