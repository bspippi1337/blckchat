CC ?= cc
CFLAGS ?= -O2 -std=c11 -Wall -Wextra -Werror -pedantic
CPPFLAGS ?= -Iinclude
CRYPTO_CFLAGS ?= $(shell pkg-config --cflags openssl 2>/dev/null)
CRYPTO_LIBS ?= $(shell pkg-config --libs openssl 2>/dev/null)
ifeq ($(strip $(CRYPTO_LIBS)),)
CRYPTO_LIBS = -lcrypto
endif

.PHONY: all test clean preview
all: blckchat blckchat-gui-preview blckchat-sms-test blckchat-authz-test blckchat-room-test blckchat-security-test

blckchat: src/main.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ $<

blckchat-gui-preview: examples/gui_preview.c src/blckui.c include/blckui.h
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ examples/gui_preview.c src/blckui.c

blckchat-sms-test: tests/sms.c src/blcksms.c include/blcksms.h
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ tests/sms.c src/blcksms.c

blckchat-authz-test: tests/authz.c src/blckauthz.c include/blckauthz.h
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ tests/authz.c src/blckauthz.c

blckchat-room-test: tests/room.c src/blckroom.c src/blcksecure_openssl.c src/blcksms.c include/blckroom.h include/blcksecure.h include/blcksms.h
	$(CC) $(CPPFLAGS) $(CRYPTO_CFLAGS) $(CFLAGS) -o $@ tests/room.c src/blckroom.c src/blcksecure_openssl.c src/blcksms.c $(CRYPTO_LIBS)

blckchat-security-test: tests/security_edge.c src/blckroom.c src/blcksecure_openssl.c src/blcksms.c include/blckroom.h include/blcksecure.h include/blcksms.h
	$(CC) $(CPPFLAGS) $(CRYPTO_CFLAGS) $(CFLAGS) -o $@ tests/security_edge.c src/blckroom.c src/blcksecure_openssl.c src/blcksms.c $(CRYPTO_LIBS)

preview: blckchat-gui-preview
	./blckchat-gui-preview blckchat-preview.svg

test: all
	sh tests/smoke.sh
	./blckchat-sms-test
	./blckchat-authz-test
	./blckchat-room-test
	./blckchat-security-test
	./blckchat-gui-preview blckchat-preview.svg
	grep -q 'BLCK / CHAT' blckchat-preview.svg

clean:
	rm -f blckchat blckchat-gui-preview blckchat-sms-test blckchat-authz-test blckchat-room-test blckchat-preview.svg
