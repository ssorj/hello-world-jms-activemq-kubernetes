.PHONY: test
test:
	scripts/test

README.html: README.md
	pandoc -o $@ $<
