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

add-word:
	@make -C $(ROOT) add-word ARGS="$(ARGS)"

run-api-args: $(JAR_FILE)
	@make -C $(ROOT) run-api $(ARGS)
