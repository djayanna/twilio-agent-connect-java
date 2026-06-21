#!/bin/bash

# Twilio Agent Connect - Credential Checker
# This script verifies that all required environment variables are set

echo "================================================"
echo "Twilio Agent Connect - Credential Check"
echo "================================================"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track if all required credentials are present
ALL_SET=true

# Function to check a credential
check_credential() {
    local var_name=$1
    local var_value="${!var_name}"
    local is_required=$2

    if [ -z "$var_value" ]; then
        if [ "$is_required" = "required" ]; then
            echo -e "${RED}✗${NC} $var_name: ${RED}NOT SET (REQUIRED)${NC}"
            ALL_SET=false
        else
            echo -e "${YELLOW}○${NC} $var_name: not set (optional)"
        fi
    else
        # Show only first 5 characters for security
        local preview="${var_value:0:5}..."
        echo -e "${GREEN}✓${NC} $var_name: $preview"
    fi
}

echo "Required Credentials:"
echo "-------------------"
check_credential "TWILIO_ACCOUNT_SID" "required"
check_credential "TWILIO_AUTH_TOKEN" "required"
check_credential "TWILIO_API_KEY" "required"
check_credential "TWILIO_API_SECRET" "required"
check_credential "TWILIO_CONVERSATION_CONFIGURATION_ID" "required"
check_credential "TWILIO_PHONE_NUMBER" "required"

echo ""
echo "Optional Credentials:"
echo "-------------------"
check_credential "TWILIO_VOICE_PUBLIC_DOMAIN" "optional"
check_credential "TWILIO_MEMORY_STORE_ID" "optional"
check_credential "TWILIO_TRAIT_GROUPS" "optional"

echo ""
echo "================================================"

if [ "$ALL_SET" = true ]; then
    echo -e "${GREEN}✓ All required credentials are set!${NC}"
    echo ""
    echo "You can now run the application:"
    echo "  ./gradlew bootRun"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some required credentials are missing!${NC}"
    echo ""
    echo "To set them up:"
    echo "  1. Read SETUP_CREDENTIALS.md for detailed instructions"
    echo "  2. Copy .env.example to .env and fill in your values"
    echo "  3. Load them: export \$(cat .env | xargs)"
    echo "  4. Run this script again to verify"
    echo ""
    exit 1
fi
