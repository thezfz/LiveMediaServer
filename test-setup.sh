#!/bin/bash

# Live Media Server Test Script
# This script tests the basic functionality of the Live Media Server

# set -e  # Don't exit on error, we want to show all test results

echo "🧪 Live Media Server Test Script"
echo "==============================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Test 1: Check if podman deploy script exists and is executable
test_build_script() {
    print_status "Testing podman deploy script..."
    if [ -x "./podman-deploy.sh" ]; then
        print_success "Podman deploy script is executable"
    else
        print_error "Podman deploy script is not executable"
        return 1
    fi
}

# Test 2: Check directory structure
test_directory_structure() {
    print_status "Testing directory structure..."
    
    local dirs=("rtmp-server" "web-api-server" "transcoder" "media-data")
    local all_good=true
    
    for dir in "${dirs[@]}"; do
        if [ -d "$dir" ]; then
            print_success "Directory exists: $dir"
        else
            print_error "Directory missing: $dir"
            all_good=false
        fi
    done
    
    if [ "$all_good" = true ]; then
        return 0
    else
        return 1
    fi
}

# Test 3: Check RTMP server source files
test_rtmp_source() {
    print_status "Testing RTMP server source files..."
    
    local files=("rtmp-server/src/com/example/rtmpserver/Server.java" 
                 "rtmp-server/src/com/example/rtmpserver/RtmpHandler.java"
                 "rtmp-server/src/com/example/rtmpserver/Amf0Utils.java")
    local all_good=true
    
    for file in "${files[@]}"; do
        if [ -f "$file" ]; then
            print_success "Source file exists: $(basename $file)"
        else
            print_error "Source file missing: $file"
            all_good=false
        fi
    done
    
    if [ "$all_good" = true ]; then
        return 0
    else
        return 1
    fi
}

# Test 4: Check Web API server files
test_web_api_source() {
    print_status "Testing Web API server files..."
    
    local files=("web-api-server/pom.xml"
                 "web-api-server/src/main/java/com/example/livemediaserver/LiveMediaServerApplication.java"
                 "web-api-server/src/main/resources/application.yml")
    local all_good=true
    
    for file in "${files[@]}"; do
        if [ -f "$file" ]; then
            print_success "Web API file exists: $(basename $file)"
        else
            print_error "Web API file missing: $file"
            all_good=false
        fi
    done
    
    if [ "$all_good" = true ]; then
        return 0
    else
        return 1
    fi
}

# Test 5: Check Podman installation
test_podman() {
    print_status "Testing Podman installation..."

    if command -v podman &> /dev/null; then
        PODMAN_VERSION=$(podman --version)
        print_success "Podman installed: $PODMAN_VERSION"
        return 0
    else
        print_error "Podman not installed. Run: sudo dnf install podman"
        return 1
    fi
}

# Test 6: Check if ports are available
test_ports() {
    print_status "Testing port availability..."
    
    # Check port 1935 (RTMP)
    if ! netstat -tuln 2>/dev/null | grep -q ":1935 "; then
        print_success "Port 1935 (RTMP) is available"
    else
        print_error "Port 1935 (RTMP) is already in use"
        return 1
    fi
    
    # Check port 8080 (Web API)
    if ! netstat -tuln 2>/dev/null | grep -q ":8080 "; then
        print_success "Port 8080 (Web API) is available"
    else
        print_error "Port 8080 (Web API) is already in use"
        return 1
    fi
}

# Run all tests
run_all_tests() {
    local tests=("test_build_script" "test_directory_structure" "test_rtmp_source"
                 "test_web_api_source" "test_podman" "test_ports")
    local passed=0
    local total=${#tests[@]}

    echo ""
    for test in "${tests[@]}"; do
        if $test; then
            ((passed++))
        fi
        echo ""
    done

    echo "==============================="
    echo -e "Test Results: ${GREEN}$passed${NC}/${total} tests passed"

    if [ $passed -eq $total ]; then
        echo -e "${GREEN}✅ All tests passed! Your Live Media Server setup is ready.${NC}"
        echo ""
        echo "Next steps (容器化部署):"
        echo "1. Run: ./podman-deploy.sh build"
        echo "2. Run: ./podman-deploy.sh start"
        echo "3. Configure OBS with: rtmp://localhost:1935/live"
        echo "4. Test API with: curl http://localhost:8080/api/streams"
        echo ""
        echo "Alternative (开发模式):"
        echo "1. cd rtmp-server && javac -d bin src/com/example/rtmpserver/*.java && java -cp bin com.example.rtmpserver.Server"
        echo "2. cd web-api-server && mvn spring-boot:run"
        return 0
    else
        echo -e "${RED}❌ Some tests failed. Please check the errors above.${NC}"
        return 1
    fi
}

# Main execution
run_all_tests
