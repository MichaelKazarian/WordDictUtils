# =====================================================
#  WordDictUtils — Makefile wrapper for Maven
# =====================================================

APP_NAME  := WordDictUtils
VERSION   := 1.0.0
JAR_FILE  := target/$(APP_NAME)-$(VERSION).jar

.PHONY: help
help:
	@echo "=== $(APP_NAME) Commands ==="
	@echo " make build        — Build fat JAR with dependencies"
	@echo " make run          — Run main class from the JAR"
	@echo " make import-org   — Run import-org task"
	@echo " make test         — Run tests"
	@echo " make clean        — Clean build artifacts"

build:
	@mvn -q clean package -DskipTests=false
	@echo "✅ Built $(JAR_FILE)"

run: $(JAR_FILE)
	@java -jar $(JAR_FILE)

import-org: $(JAR_FILE)
	@java -jar $(JAR_FILE) import-org $(INPUT) $(OUTPUT)

test:
	@mvn -q test

clean:
	@mvn -q clean
