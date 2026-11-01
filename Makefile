SHELL := /bin/sh

GRADLE ?= ./gradlew
NPM ?= npm

TAILWIND_VERSION ?= 4.3.3
TAILWIND_INPUT ?= tailwind/input.css
TAILWIND_OUTPUT ?= src/main/resources/static/css/style.css
TAILWIND_CACHE ?= $(HOME)/.cache/selfdrop/tailwind-$(TAILWIND_VERSION)

.DEFAULT_GOAL := help

.PHONY: help tailwind-deps css css-watch build test check run dev clean clean-data clean-tailwind

## Show the available targets.
help:
	@echo "Selfdrop"
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "  make css             Regenerate the committed Tailwind CSS file"
	@echo "  make css-watch       Regenerate CSS while editing templates or app.js"
	@echo "  make build           Compile, test, and assemble the application"
	@echo "  make test            Run the test suite"
	@echo "  make check           Alias for test"
	@echo "  make run             Start the application on http://localhost:8080/"
	@echo "  make dev             Run css-watch and the application together"
	@echo "  make clean           Remove Gradle build output"
	@echo "  make clean-data      Remove the local SQLite database and stored files"
	@echo "  make clean-tailwind  Remove the cached Tailwind CLI"
	@echo ""
	@echo "Variables: GRADLE, NPM, TAILWIND_VERSION, TAILWIND_INPUT,"
	@echo "           TAILWIND_OUTPUT, TAILWIND_CACHE"

## Install the Tailwind CLI into a cache directory outside the repository.
tailwind-deps:
	@if [ ! -x "$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" ]; then \
		echo "Installing Tailwind CLI $(TAILWIND_VERSION) into $(TAILWIND_CACHE)"; \
		$(NPM) install --prefix "$(TAILWIND_CACHE)" \
			--no-save --no-package-lock --silent \
			tailwindcss@$(TAILWIND_VERSION) @tailwindcss/cli@$(TAILWIND_VERSION); \
	fi

## Regenerate the committed Tailwind CSS file.
css: tailwind-deps
	NODE_PATH="$(TAILWIND_CACHE)/node_modules" \
		"$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" \
		-i $(TAILWIND_INPUT) \
		-o $(TAILWIND_OUTPUT) \
		--minify

## Regenerate CSS while editing templates or app.js.
css-watch: tailwind-deps
	NODE_PATH="$(TAILWIND_CACHE)/node_modules" \
		"$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" \
		-i $(TAILWIND_INPUT) \
		-o $(TAILWIND_OUTPUT) \
		--watch

## Compile, test, and assemble the application.
build:
	$(GRADLE) build

## Run the test suite.
test:
	$(GRADLE) test

## Alias for test.
check: test

## Start the application.
run:
	$(GRADLE) bootRun

## Run css-watch and the application together.
dev:
	$(MAKE) -j2 css-watch run

## Remove Gradle build output.
clean:
	$(GRADLE) clean

## Remove the local SQLite database and stored files.
clean-data:
	rm -rf data

## Remove the cached Tailwind CLI.
clean-tailwind:
	rm -rf "$(TAILWIND_CACHE)"
