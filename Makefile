.PHONY: default
default:
	@echo "Targets: build, test, clean, readme"

.PHONY: build
build:
	cd sender && mvn package
	cd receiver && mvn package

.PHONY: test
test: build
	scripts/test

.PHONY: clean
clean:
	rm -rf sender/target
	rm -rf receiver/target
	rm -f README.html

.PHONY: readme
readme: README.html

README.html: README.md
	pandoc -o $@ $<
