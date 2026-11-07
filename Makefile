# Seefi Android - Makefile
# Run `make help` for available targets

.PHONY: help build test lint release clean install signed-release

help:
	@echo "Seefi Android - Build targets"
	@echo ""
	@echo "  make build           Debug builds (assembleDebug)"
	@echo "  make test            Run unit tests (testDebugUnitTest)"
	@echo "  make lint            Run Android lint"
	@echo "  make release         Build release APK (unsigned without signing env)"
	@echo "  make install         Install debug build on connected device"
	@echo "  make clean           Remove build artifacts"

build:
	./gradlew assembleDebug

test:
	./gradlew testDebugUnitTest

lint:
	./gradlew lintDebug

release:
	./gradlew assembleRelease

install:
	./gradlew installDebug

clean:
	./gradlew clean
