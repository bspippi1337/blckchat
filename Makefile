CC ?= cc
CFLAGS ?= -O2 -std=c11 -Wall -Wextra -Werror -pedantic
CPPFLAGS ?= -Iinclude
.PHONY: all test clean preview
all: blckchat blckchat-gui-preview
blckchat: src/main.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ $<
blckchat-gui-preview: examples/gui_preview.c src/blckui.c include/blckui.h
	$(CC) $(CPPFLAGS) $(CFLAGS) -o $@ examples/gui_preview.c src/blckui.c
preview: blckchat-gui-preview
	./blckchat-gui-preview blckchat-preview.svg
test: all
	sh tests/smoke.sh
	./blckchat-gui-preview blckchat-preview.svg
	grep -q 'BLCK / CHAT' blckchat-preview.svg
clean:
	rm -f blckchat blckchat-gui-preview blckchat-preview.svg
