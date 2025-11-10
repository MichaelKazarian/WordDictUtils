# =====================================================
#  WordDictUtils — Makefile wrapper for Maven
# =====================================================

# --- Project Configuration ---
# Update these constants for the new project
APP_NAME = WordDictUtils
VERSION = 1.0.0-gpl
JAR_FILE = target/$(APP_NAME)-$(VERSION).jar

# ------------------------
# Default target
# ------------------------
.PHONY: help
help:
	@echo ""
	@echo "=== $(APP_NAME) Build Commands ==="
	@echo " make build	 — Build the JAR package"
	@echo " make test	 — Run all tests"
	@echo " make clean	 — Clean build artifacts"
	@echo " make install	 — Install to local Maven repository (~/.m2)"
	@echo " make deploy	 — Deploy to GitHub Packages (if configured)"
	@echo " make run MAIN=<class> — Run a specific main class (requires exec plugin in pom.xml)"
	@echo " make all	 — Clean, build, test, and package"
	@echo ""

# ------------------------
# Build, Test, Clean
# ------------------------

build:
	mvn clean package -DskipTests=false

test:
	mvn test

clean:
	mvn clean

# ------------------------
# Install / Deploy
# ------------------------

install:
	mvn install

deploy:
	mvn deploy

# ------------------------
# Run (with main class)
# ------------------------

run:
	@if [ -z "$(MAIN)" ]; then \
		echo "Please specify MAIN class, e.g.:"; \
		echo " make run MAIN=com.worddict.worddictutils.ExampleRunner"; \
	else \
		mvn exec:java -Dexec.mainClass=$(MAIN); \
	fi

# ------------------------
# Utility targets
# ------------------------

jar:
	@echo "Built JAR file: $(JAR_FILE)"

all: clean build test jar
