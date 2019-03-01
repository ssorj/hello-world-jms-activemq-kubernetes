.PHONY: build
build:
	docker build -t receiver .

.PHONY: run
run: build
	docker run --rm -p 8080:8080 receiver

.PHONY: push
push:
