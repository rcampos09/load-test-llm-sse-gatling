#!/bin/bash

# Script para ejecutar el flujo completo de load testing y análisis de calidad
# Sprint 1: Basic consistency analysis

set -e  # Exit on error

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  LLM Load Testing & Quality Analysis - Sprint 1             ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if API key is set
if [ -z "$api_key" ]; then
    echo -e "${RED}❌ Error: api_key environment variable not set${NC}"
    echo "Please set your OpenAI API key:"
    echo "  export api_key=your_openai_key"
    exit 1
fi

# Step 1: Compile the project
echo -e "${YELLOW}📦 Step 1: Compiling project...${NC}"
./mvnw clean compile test-compile
echo -e "${GREEN}✅ Compilation completed${NC}"
echo ""

# Step 2: Run the Gatling load test
echo -e "${YELLOW}🧪 Step 2: Running Gatling load test...${NC}"
echo "   This may take 1-2 minutes depending on configuration"
./mvnw gatling:test
echo -e "${GREEN}✅ Load test completed${NC}"
echo ""

# Step 3: Aggregate responses by prompt
echo -e "${YELLOW}📊 Step 3: Aggregating responses by prompt...${NC}"
./mvnw exec:java -Dexec.mainClass="ssellm.ResponseAggregator" -Dexec.classpathScope=test
echo -e "${GREEN}✅ Response aggregation completed${NC}"
echo ""

# Step 4: Run consistency analysis
echo -e "${YELLOW}🔍 Step 4: Analyzing response consistency...${NC}"
./mvnw exec:java -Dexec.mainClass="ssellm.ConsistencyAnalyzer" -Dexec.classpathScope=test
echo -e "${GREEN}✅ Consistency analysis completed${NC}"
echo ""

# Step 5: Display summary
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Analysis Complete - Reports Generated                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo -e "${GREEN}📄 Generated Reports:${NC}"
echo "   1. target/llm_response.txt           - Human-readable responses"
echo "   2. target/responses_metadata.jsonl   - Structured metadata (JSONL)"
echo "   3. target/responses_by_prompt.json   - Responses grouped by prompt"
echo "   4. target/consistency_analysis.json  - Consistency analysis report"
echo "   5. target/gatling/*/index.html       - Gatling performance report"
echo ""

# Display quick stats if jq is available
if command -v jq &> /dev/null; then
    echo -e "${YELLOW}📈 Quick Stats:${NC}"

    if [ -f "target/consistency_analysis.json" ]; then
        GLOBAL_SCORE=$(jq -r '.global_consistency_score' target/consistency_analysis.json)
        TOTAL_RESPONSES=$(jq -r '.total_responses' target/consistency_analysis.json)
        SUMMARY=$(jq -r '.summary' target/consistency_analysis.json)

        echo "   Global Consistency Score: $GLOBAL_SCORE"
        echo "   Total Responses Analyzed: $TOTAL_RESPONSES"
        echo "   Summary: $SUMMARY"
    fi
    echo ""
fi

# Open reports in browser (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${YELLOW}🌐 Opening reports in browser...${NC}"

    # Find the latest Gatling report
    LATEST_REPORT=$(find target/gatling -name "index.html" -type f | sort -r | head -n 1)

    if [ -n "$LATEST_REPORT" ]; then
        open "$LATEST_REPORT"
    fi
fi

echo ""
echo -e "${GREEN}✅ All tasks completed successfully!${NC}"
echo ""
echo "Next steps:"
echo "  - Review the consistency_analysis.json report"
echo "  - Check the Gatling HTML report for performance metrics"
echo "  - Compare consistency scores across different test runs"
echo ""
