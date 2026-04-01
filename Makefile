# =====================================================
#  WordDictUtils — Makefile wrapper for Maven
# =====================================================

# Disable "Entering directory" messages
MAKEFLAGS += --no-print-directory

APP_NAME  := WordDictUtils
VERSION   := 1.0.0
JAR_FILE  := target/$(APP_NAME)-$(VERSION).jar
MVN       := ./mvnw
MVN_VER   := 3.9.6

.PHONY: help
help:
	@echo "=== $(APP_NAME) Commands ==="
	@echo " make build        — Build fat JAR with dependencies"
	@echo " make run          — Run main class from the JAR"
	@echo " make import-org   — Run import-org task"
	@echo " make create-dict  — Run create-dict task"
	@echo " make list-dicts   — Run list-dict task"
	@echo " make test         — Run tests"
	@echo " make clean        — Clean build artifacts"

build: setup-wrapper
	@$(MVN) -q clean package -DskipTests=false
	@echo "Built $(JAR_FILE)"

setup-wrapper:
	@if [ ! -f $(MVN) ]; then \
		echo "Initializing Maven Wrapper $(MVN_VER)..."; \
		mvn wrapper:wrapper -Dmaven=$(MVN_VER); \
	fi

run: $(JAR_FILE)
	@java -jar $(JAR_FILE)

import-org: $(JAR_FILE)
	@java -jar $(JAR_FILE) import-org $(INPUT) $(OUTPUT)

#make create-dict DICTDIR=my-dict SRC=en TARGET=es
create-dict: $(JAR_FILE)
	@java -jar $(JAR_FILE) create-dict $(DICTDIR) $(SRC) $(TARGET)

# create-dict-args: $(JAR_FILE)
# 	@java -jar $(JAR_FILE) create-dict dictdir en uk -n "Test name"

# make create-dict-args ARGS="dict-path en es -n 'test name'"
create-dict-args: $(JAR_FILE)
	@java -jar $(JAR_FILE) create-dict $(ARGS)

#make list-dicts DICTDIR=my-dict
list-dicts: $(JAR_FILE)
	@java -jar $(JAR_FILE) list-dicts $(DICTDIR) $(SRC)

#make list-dicts-args ARGS="my-dict -s en"
list-dicts-args: $(JAR_FILE)
	@java -jar $(JAR_FILE) list-dicts $(ARGS)

add-word: $(JAR_FILE)
	@java -Dupdate.stats=true -jar $(JAR_FILE) add-word $(ARGS)

remove-word: $(JAR_FILE)
	@java -Dupdate.stats=true -jar $(JAR_FILE) remove-word $(ARGS)

run-api:
	mvn exec:java -Dexec.mainClass="com.worddict.web.DictionaryWebApi"

test:
	@if [ -f $(MVN) ]; then $(MVN) -q test; else mvn -q test; fi

clean:
	@echo "Cleaning project artifacts..."
	@if [ -f ./mvnw ]; then ./mvnw -q clean; else mvn -q clean; fi
	@echo "Removing Maven Wrapper and local Maven distribution..."
	@rm -rf .mvn
	@rm -f mvnw
	@rm -f mvnw.cmd
	@echo "Project is now 'factory clean'."
