CC ?= cc
CFLAGS ?= -O2 -std=c11 -Wall -Wextra -Werror -pedantic
.PHONY: all test clean
all: blckchat
blckchat: src/main.c
	$(CC) $(CFLAGS) -o $@ $<
test: blckchat
	sh tests/smoke.sh
clean:
	rm -f blckchat
