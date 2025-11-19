#!/bin/bash

# Extract API key
export api_key=$(grep "api_key" .env | head -1 | cut -d'=' -f2 | tr -d '"')

# Run quality report generator with NEW Sprint 2 data
java -cp "target/test-classes:$(cat classpath.txt)" \
  ssellm.analyzers.QualityReportGenerator \
  target/responses_metadata.jsonl \
  quality_report_sprint2_new.json
