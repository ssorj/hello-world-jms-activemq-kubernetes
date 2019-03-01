.PHONY: build
build:
	docker build -t sender .

.PHONY: run
run: build
	docker run --rm -p 8080:8080 sender

.PHONY: push
push:
