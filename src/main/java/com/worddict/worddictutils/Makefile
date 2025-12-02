# Disable "Entering directory" messages
MAKEFLAGS += --no-print-directory

# Auto-detect project root by searching for pom.xml
ROOT := $(shell \
    dir=$$PWD; \
    while [ "$$dir" != "/" ]; do \
        if [ -f "$$dir/pom.xml" ]; then \
            echo $$dir; \
            exit 0; \
        fi; \
        dir=$$(dirname "$$dir"); \
    done; \
)

# Delegate targets to the root Makefile

.PHONY: build test clean import-org create-dict

build:
	@make -C $(ROOT) build

test:
	@make -C $(ROOT) test

clean:
	@make -C $(ROOT) clean


import-org:
	@make -C $(ROOT) import-org INPUT="$(INPUT)" OUTPUT="$(OUTPUT)"

create-dict:
	@make -C $(ROOT) create-dict DICTDIR="$(DICTDIR)" SRC="$(SRC)" TARGET="$(TARGET)"

create-dict-args:
	@make -C $(ROOT) create-dict-args ARGS="$(ARGS)"

