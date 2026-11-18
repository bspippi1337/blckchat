#ifndef BLCKUI_H
#define BLCKUI_H
/* BLCKUI: independent C11 scene command and theme layer.
   No platform, graphics, font or third-party headers in public API. */
#include <stddef.h>
#include <stdint.h>

#define BSUI_MAX_COMMANDS 512u
#define BSUI_MAX_TEXT 192u

typedef struct { float x, y, w, h; } bsui_rect;
typedef struct { uint8_t r, g, b, a; } bsui_color;
typedef enum {
    BSUI_RECT = 1, BSUI_TEXT = 2, BSUI_ROUNDED_RECT = 3
} bsui_kind;
typedef enum {
    BSUI_TEXT_BODY = 0, BSUI_TEXT_CAPTION = 1, BSUI_TEXT_TITLE = 2
} bsui_text_style;
typedef struct {
    bsui_kind kind;
    bsui_rect rect;
    bsui_color color;
    float radius;
    bsui_text_style text_style;
    char text[BSUI_MAX_TEXT];
} bsui_command;
typedef struct {
    bsui_color background, panel, elevated, accent, foreground;
    bsui_color secondary, border, sender, receiver, composer;
    float spacing, radius, sidebar;
} bsui_theme;
typedef struct {
    bsui_command commands[BSUI_MAX_COMMANDS];
    size_t count;
    float width, height;
    bsui_theme theme;
} bsui_scene;
typedef struct {
    const char *author;
    const char *message;
    int mine;
} bsui_message;

bsui_theme bsui_theme_dark(void);
bsui_theme bsui_theme_light(void);
void bsui_scene_init(bsui_scene *scene, float width, float height, bsui_theme theme);
int bsui_box(bsui_scene *scene, bsui_rect rect, bsui_color color, float radius);
int bsui_label(bsui_scene *scene, float x, float y,
               const char *text, bsui_color color, bsui_text_style style);
int bsui_chat_layout(bsui_scene *scene, const char *room,
                     const bsui_message *messages, size_t message_count);
/* Portable reference raster-independent exporter, not a native windowing backend. */
int bsui_export_svg(const bsui_scene *scene, const char *filename);
#endif
