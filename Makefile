.PHONY: test server-test pipeline-test android-test

test: pipeline-test server-test

pipeline-test:
	python3 -m unittest discover -s content-pipeline/tests -v

server-test:
	cd server && go test ./...

android-test:
	cd android && ./gradlew test

