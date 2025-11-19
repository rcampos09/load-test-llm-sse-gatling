#!/bin/bash

# Load API key from .env
export api_key=$(grep "api_key" .env | cut -d'=' -f2 | tr -d '"')

# Run Gatling test
./mvnw gatling:test -Dgatling.simulationClass=ssellm.SSELLM
