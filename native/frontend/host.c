#include <dlfcn.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libretro.h"
#include "host.h"

#ifdef __ANDROID__
#include <android/log.h>
#define HLOG(...) __android_log_print(ANDROID_LOG_INFO, "KonsolHost", __VA_ARGS__)
#else
#define HLOG(...) do { fprintf(stderr, "[host] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#endif

#define CMD(c) ((c) & 0xFFFF)

/* ---- core entry points ---- */
static struct {
    void *handle;
    void (*retro_init)(void);
    void (*retro_deinit)(void);
    unsigned (*retro_api_version)(void);
    void (*retro_get_system_info)(struct retro_system_info *);
    void (*retro_get_system_av_info)(struct retro_system_av_info *);
    void (*retro_run)(void);
    void (*retro_reset)(void);
    bool (*retro_load_game)(const struct retro_game_info *);
    void (*retro_unload_game)(void);
    void (*retro_set_environment)(retro_environment_t);
    void (*retro_set_video_refresh)(retro_video_refresh_t);
    void (*retro_set_audio_sample)(retro_audio_sample_t);
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*retro_set_input_poll)(retro_input_poll_t);
    void (*retro_set_input_state)(retro_input_state_t);
    size_t (*retro_serialize_size)(void);
    bool (*retro_serialize)(void *, size_t);
    bool (*retro_unserialize)(const void *, size_t);
    void *(*retro_get_memory_data)(unsigned);
    size_t (*retro_get_memory_size)(unsigned);
    void (*retro_cheat_reset)(void);
    void (*retro_cheat_set)(unsigned, bool, const char *);
} core;

static char g_error[512];
static char g_system_dir[1024];
static char g_save_dir[1024];
static int g_loaded_core = 0;
static int g_loaded_game = 0;
static void *g_game_data = NULL;

static enum retro_pixel_format g_fmt = RETRO_PIXEL_FORMAT_0RGB1555;

/* video */
static uint32_t *g_rgba = NULL;
static size_t g_rgba_cap = 0;
static int g_w = 0, g_h = 0;
static int g_frames = 0;
static float g_aspect = 0.f;
static double g_fps = 60.0, g_rate = 44100.0;

/* audio queue (stereo frames) */
#define AUDIO_CAP_FRAMES 32768
static int16_t g_audio[AUDIO_CAP_FRAMES * 2];
static int g_audio_frames = 0;

/* input */
static uint32_t g_buttons = 0;

/* core options */
#define OPT_MAX 48
static char g_opt_key[OPT_MAX][64];
static char g_opt_val[OPT_MAX][64];
static int g_opt_n = 0;

static void set_error(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(g_error, sizeof(g_error), fmt, ap);
    va_end(ap);
    HLOG("ERROR: %s", g_error);
}

const char *host_last_error(void) { return g_error; }

void host_set_option(const char *key, const char *value)
{
    if (!key || !value) return;
    for (int i = 0; i < g_opt_n; i++) {
        if (strcmp(g_opt_key[i], key) == 0) {
            snprintf(g_opt_val[i], sizeof(g_opt_val[i]), "%s", value);
            return;
        }
    }
    if (g_opt_n >= OPT_MAX) return;
    snprintf(g_opt_key[g_opt_n], sizeof(g_opt_key[0]), "%s", key);
    snprintf(g_opt_val[g_opt_n], sizeof(g_opt_val[0]), "%s", value);
    g_opt_n++;
}

/* ---- callbacks ---- */
static void RETRO_CALLCONV log_cb(enum retro_log_level level, const char *fmt, ...)
{
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    size_t n = strlen(buf);
    while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = 0;
    HLOG("core[%d]: %s", (int)level, buf);
}

static bool RETRO_CALLCONV env_cb(unsigned cmd, void *data)
{
    switch (CMD(cmd)) {
    case CMD(RETRO_ENVIRONMENT_SHUTDOWN):
    case CMD(RETRO_ENVIRONMENT_SET_ROTATION):
    case CMD(RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL):
    case CMD(RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS):
    case CMD(RETRO_ENVIRONMENT_SET_VARIABLES):
    case CMD(RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME):
    case CMD(RETRO_ENVIRONMENT_SET_CONTROLLER_INFO):
    case CMD(RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS):
    case CMD(RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO):
    case CMD(RETRO_ENVIRONMENT_SET_MEMORY_MAPS):
    case CMD(RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE):
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_CAN_DUPE):
        *(bool *)data = true;
        return true;

    case CMD(RETRO_ENVIRONMENT_SET_MESSAGE): {
        const struct retro_message *m = (const struct retro_message *)data;
        if (m && m->msg) HLOG("message: %s", m->msg);
        return true;
    }
    case CMD(RETRO_ENVIRONMENT_SET_MESSAGE_EXT): {
        const struct retro_message_ext *m = (const struct retro_message_ext *)data;
        if (m && m->msg) HLOG("message: %s", m->msg);
        return true;
    }

    case CMD(RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY):
    case CMD(RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY):
        *(const char **)data = g_system_dir;
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY):
        *(const char **)data = g_save_dir;
        return true;

    case CMD(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT): {
        enum retro_pixel_format f = *(const enum retro_pixel_format *)data;
        if (f == RETRO_PIXEL_FORMAT_XRGB8888 || f == RETRO_PIXEL_FORMAT_RGB565 ||
            f == RETRO_PIXEL_FORMAT_0RGB1555) {
            g_fmt = f;
            return true;
        }
        return false;
    }

    case CMD(RETRO_ENVIRONMENT_GET_VARIABLE): {
        struct retro_variable *v = (struct retro_variable *)data;
        if (v && v->key) {
            for (int i = 0; i < g_opt_n; i++) {
                if (strcmp(g_opt_key[i], v->key) == 0) {
                    v->value = g_opt_val[i];
                    return true;
                }
            }
        }
        if (v) v->value = NULL;
        return false;
    }
    case CMD(RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE):
        *(bool *)data = false;
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES):
        *(uint64_t *)data = (1ULL << RETRO_DEVICE_JOYPAD);
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_LOG_INTERFACE):
        ((struct retro_log_callback *)data)->log = log_cb;
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION):
        *(unsigned *)data = 0;
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION):
        *(unsigned *)data = 0;
        return true;

    case CMD(RETRO_ENVIRONMENT_GET_LANGUAGE):
        *(unsigned *)data = RETRO_LANGUAGE_ENGLISH;
        return true;

    case CMD(RETRO_ENVIRONMENT_SET_GEOMETRY): {
        const struct retro_game_geometry *g = (const struct retro_game_geometry *)data;
        if (g && g->aspect_ratio > 0.f) g_aspect = g->aspect_ratio;
        return true;
    }
    case CMD(RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO): {
        const struct retro_system_av_info *a = (const struct retro_system_av_info *)data;
        if (a) {
            if (a->timing.fps > 1.0) g_fps = a->timing.fps;
            if (a->timing.sample_rate > 1000.0) g_rate = a->timing.sample_rate;
            if (a->geometry.aspect_ratio > 0.f) g_aspect = a->geometry.aspect_ratio;
        }
        return true;
    }

    default:
        return false; /* hardware rendering, VFS, disk control, ... not offered */
    }
}

static void RETRO_CALLCONV video_cb(const void *data, unsigned w, unsigned h, size_t pitch)
{
    if (!data || data == RETRO_HW_FRAME_BUFFER_VALID || !w || !h) return; /* duped frame */

    size_t need = (size_t)w * h;
    if (need > g_rgba_cap) {
        free(g_rgba);
        g_rgba = (uint32_t *)malloc(need * 4);
        g_rgba_cap = g_rgba ? need : 0;
        if (!g_rgba) return;
    }
    g_w = (int)w;
    g_h = (int)h;

    for (unsigned y = 0; y < h; y++) {
        uint32_t *out = g_rgba + (size_t)y * w;
        if (g_fmt == RETRO_PIXEL_FORMAT_XRGB8888) {
            const uint32_t *in = (const uint32_t *)((const uint8_t *)data + y * pitch);
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = in[x];
                uint32_t r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                out[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        } else if (g_fmt == RETRO_PIXEL_FORMAT_RGB565) {
            const uint16_t *in = (const uint16_t *)((const uint8_t *)data + y * pitch);
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = in[x];
                uint32_t r = (p >> 11) & 0x1F, g = (p >> 5) & 0x3F, b = p & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 2) | (g >> 4);
                b = (b << 3) | (b >> 2);
                out[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        } else { /* 0RGB1555 */
            const uint16_t *in = (const uint16_t *)((const uint8_t *)data + y * pitch);
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = in[x];
                uint32_t r = (p >> 10) & 0x1F, g = (p >> 5) & 0x1F, b = p & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 3) | (g >> 2);
                b = (b << 3) | (b >> 2);
                out[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    }
    g_frames++;
}

static void push_audio(const int16_t *d, size_t frames)
{
    if (frames > AUDIO_CAP_FRAMES) {
        d += (frames - AUDIO_CAP_FRAMES) * 2;
        frames = AUDIO_CAP_FRAMES;
    }
    if (g_audio_frames + (int)frames > AUDIO_CAP_FRAMES) { /* drop oldest */
        int drop = g_audio_frames + (int)frames - AUDIO_CAP_FRAMES;
        memmove(g_audio, g_audio + drop * 2, (size_t)(g_audio_frames - drop) * 4);
        g_audio_frames -= drop;
    }
    memcpy(g_audio + (size_t)g_audio_frames * 2, d, frames * 4);
    g_audio_frames += (int)frames;
}

static void RETRO_CALLCONV audio_cb(int16_t l, int16_t r)
{
    int16_t s[2] = { l, r };
    push_audio(s, 1);
}

static size_t RETRO_CALLCONV audio_batch_cb(const int16_t *d, size_t frames)
{
    push_audio(d, frames);
    return frames;
}

static void RETRO_CALLCONV poll_cb(void) {}

static int16_t RETRO_CALLCONV input_cb(unsigned port, unsigned device, unsigned index, unsigned id)
{
    if (port != 0) return 0;
    if ((device & RETRO_DEVICE_MASK) != RETRO_DEVICE_JOYPAD) return 0;
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)(g_buttons & 0xFFFF);
    return id < 16 ? (int16_t)((g_buttons >> id) & 1) : 0;
}

/* ---- battery saves ---- */
static struct {
    unsigned id;
    const char *ext;
    uint8_t *shadow;
    size_t size;
} g_mem[2] = {
    { RETRO_MEMORY_SAVE_RAM, ".srm", NULL, 0 },
    { RETRO_MEMORY_RTC, ".rtc", NULL, 0 },
};
static char g_save_base[1024];

/* ---- rewind ring ---- */
static uint8_t **g_rw = NULL;
static int g_rw_cap = 0, g_rw_head = 0, g_rw_cnt = 0, g_rw_every = 3, g_rw_tick = 0, g_rw_on = 0;
static int g_rw_mb = 0;
static size_t g_rw_sz = 0;
static int g_rewinding = 0;

/* ---- public API ---- */
#define LOADSYM(name)                                                  \
    do {                                                               \
        *(void **)(&core.name) = dlsym(core.handle, #name);            \
        if (!core.name) { set_error("missing symbol %s", #name); return 0; } \
    } while (0)

int host_load_core(const char *core_path, const char *system_dir, const char *save_dir)
{
    memset(&core, 0, sizeof(core));
    g_error[0] = 0;
    snprintf(g_system_dir, sizeof(g_system_dir), "%s", system_dir ? system_dir : ".");
    snprintf(g_save_dir, sizeof(g_save_dir), "%s", save_dir ? save_dir : ".");
    g_w = g_h = g_frames = 0;
    g_audio_frames = 0;
    g_buttons = 0;
    g_fmt = RETRO_PIXEL_FORMAT_0RGB1555;

    core.handle = dlopen(core_path, RTLD_NOW | RTLD_LOCAL);
    if (!core.handle) { set_error("dlopen failed: %s", dlerror()); return 0; }

    LOADSYM(retro_init);
    LOADSYM(retro_deinit);
    LOADSYM(retro_api_version);
    LOADSYM(retro_get_system_info);
    LOADSYM(retro_get_system_av_info);
    LOADSYM(retro_run);
    LOADSYM(retro_reset);
    LOADSYM(retro_load_game);
    LOADSYM(retro_unload_game);
    LOADSYM(retro_set_environment);
    LOADSYM(retro_set_video_refresh);
    LOADSYM(retro_set_audio_sample);
    LOADSYM(retro_set_audio_sample_batch);
    LOADSYM(retro_set_input_poll);
    LOADSYM(retro_set_input_state);
    LOADSYM(retro_serialize_size);
    LOADSYM(retro_serialize);
    LOADSYM(retro_unserialize);
    LOADSYM(retro_get_memory_data);
    LOADSYM(retro_get_memory_size);
    LOADSYM(retro_cheat_reset);
    LOADSYM(retro_cheat_set);

    if (core.retro_api_version() != RETRO_API_VERSION) {
        set_error("core API version mismatch (%u)", core.retro_api_version());
        return 0;
    }

    core.retro_set_environment(env_cb);
    core.retro_init();
    core.retro_set_video_refresh(video_cb);
    core.retro_set_audio_sample(audio_cb);
    core.retro_set_audio_sample_batch(audio_batch_cb);
    core.retro_set_input_poll(poll_cb);
    core.retro_set_input_state(input_cb);
    g_loaded_core = 1;

    struct retro_system_info si;
    memset(&si, 0, sizeof(si));
    core.retro_get_system_info(&si);
    HLOG("core: %s %s (need_fullpath=%d)", si.library_name ? si.library_name : "?",
         si.library_version ? si.library_version : "?", (int)si.need_fullpath);
    return 1;
}

int host_load_game(const char *game_path)
{
    if (!g_loaded_core) { set_error("core not loaded"); return 0; }

    struct retro_system_info si;
    memset(&si, 0, sizeof(si));
    core.retro_get_system_info(&si);

    struct retro_game_info gi;
    memset(&gi, 0, sizeof(gi));
    gi.path = game_path;

    if (!si.need_fullpath) {
        FILE *f = fopen(game_path, "rb");
        if (!f) { set_error("cannot open game file"); return 0; }
        fseek(f, 0, SEEK_END);
        long sz = ftell(f);
        fseek(f, 0, SEEK_SET);
        g_game_data = malloc(sz > 0 ? (size_t)sz : 1);
        if (!g_game_data || fread(g_game_data, 1, (size_t)sz, f) != (size_t)sz) {
            fclose(f);
            set_error("cannot read game file");
            return 0;
        }
        fclose(f);
        gi.data = g_game_data;
        gi.size = (size_t)sz;
    }

    if (!core.retro_load_game(&gi)) {
        set_error("core refused the game file");
        return 0;
    }
    g_loaded_game = 1;

    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    core.retro_get_system_av_info(&av);
    if (av.timing.fps > 1.0) g_fps = av.timing.fps;
    if (av.timing.sample_rate > 1000.0) g_rate = av.timing.sample_rate;
    g_aspect = av.geometry.aspect_ratio;
    g_w = (int)av.geometry.base_width;
    g_h = (int)av.geometry.base_height;
    HLOG("av: %dx%d aspect=%.3f fps=%.2f rate=%.0f", g_w, g_h, g_aspect, g_fps, g_rate);
    return 1;
}

void host_set_input(uint32_t buttons) { g_buttons = buttons; }

static void rewind_capture(void)
{
    if (!g_rw_on || g_rewinding || !g_loaded_game) return;
    if (++g_rw_tick < g_rw_every) return;
    g_rw_tick = 0;
    if (g_rw_sz == 0) {
        g_rw_sz = core.retro_serialize_size();
        if (g_rw_sz == 0) { g_rw_on = 0; return; }
        g_rw_cap = (int)(((size_t)g_rw_mb * 1024u * 1024u) / g_rw_sz);
        if (g_rw_cap > 1500) g_rw_cap = 1500;
        if (g_rw_cap < 8) { g_rw_on = 0; return; }
        g_rw = (uint8_t **)calloc((size_t)g_rw_cap, sizeof(uint8_t *));
        if (!g_rw) { g_rw_on = 0; return; }
        g_rw_head = g_rw_cnt = 0;
    }
    if (!g_rw[g_rw_head]) {
        g_rw[g_rw_head] = (uint8_t *)malloc(g_rw_sz);
        if (!g_rw[g_rw_head]) { g_rw_on = 0; return; }
    }
    if (!core.retro_serialize(g_rw[g_rw_head], g_rw_sz)) return;
    g_rw_head = (g_rw_head + 1) % g_rw_cap;
    if (g_rw_cnt < g_rw_cap) g_rw_cnt++;
}

void host_run(void)
{
    if (!g_loaded_game) return;
    core.retro_run();
    rewind_capture();
}

void host_reset(void)
{
    if (!g_loaded_game) return;
    core.retro_reset();
    g_rw_cnt = 0;
    g_rw_head = 0;
}

void host_rewind_enable(int mb, int every)
{
    g_rw_mb = mb < 4 ? 4 : mb;
    g_rw_every = every < 1 ? 1 : every;
    g_rw_tick = 0;
    g_rw_on = 1;
}

int host_rewind_count(void) { return g_rw_cnt; }

int host_rewind_step(void)
{
    if (!g_loaded_game || !g_rw_on || g_rw_cnt <= 0 || !g_rw) return 0;
    int idx = (g_rw_head - 1 + g_rw_cap) % g_rw_cap;
    g_rw_head = idx;
    g_rw_cnt--;
    g_rewinding = 1;
    int ok = core.retro_unserialize(g_rw[idx], g_rw_sz) ? 1 : 0;
    if (ok) core.retro_run(); /* draw the restored frame */
    g_rewinding = 0;
    return ok;
}

/* ---- saves ---- */
void host_load_saves(const char *base)
{
    snprintf(g_save_base, sizeof(g_save_base), "%s", base ? base : "game");
    for (int i = 0; i < 2; i++) {
        size_t size = core.retro_get_memory_size(g_mem[i].id);
        uint8_t *data = (uint8_t *)core.retro_get_memory_data(g_mem[i].id);
        free(g_mem[i].shadow);
        g_mem[i].shadow = NULL;
        g_mem[i].size = 0;
        if (!size || !data) continue;
        char path[1100];
        snprintf(path, sizeof(path), "%s%s", g_save_base, g_mem[i].ext);
        FILE *f = fopen(path, "rb");
        if (f) {
            size_t n = fread(data, 1, size, f);
            fclose(f);
            HLOG("loaded %s (%zu bytes)", path, n);
        }
        g_mem[i].shadow = (uint8_t *)malloc(size);
        if (g_mem[i].shadow) {
            memcpy(g_mem[i].shadow, data, size);
            g_mem[i].size = size;
        }
    }
}

int host_flush_saves(void)
{
    if (!g_loaded_game || !g_save_base[0]) return 0;
    int written = 0;
    for (int i = 0; i < 2; i++) {
        size_t size = core.retro_get_memory_size(g_mem[i].id);
        uint8_t *data = (uint8_t *)core.retro_get_memory_data(g_mem[i].id);
        if (!size || !data) continue;
        if (g_mem[i].shadow && g_mem[i].size == size && memcmp(g_mem[i].shadow, data, size) == 0) continue;
        char path[1100], tmp[1110];
        snprintf(path, sizeof(path), "%s%s", g_save_base, g_mem[i].ext);
        snprintf(tmp, sizeof(tmp), "%s.tmp", path);
        FILE *f = fopen(tmp, "wb");
        if (!f) continue;
        size_t n = fwrite(data, 1, size, f);
        int ok = (fclose(f) == 0) && n == size;
        if (!ok) { remove(tmp); continue; }
        if (rename(tmp, path) != 0) { remove(tmp); continue; }
        if (g_mem[i].size != size) {
            free(g_mem[i].shadow);
            g_mem[i].shadow = (uint8_t *)malloc(size);
            g_mem[i].size = g_mem[i].shadow ? size : 0;
        }
        if (g_mem[i].shadow) memcpy(g_mem[i].shadow, data, size);
        written++;
    }
    return written;
}

/* ---- save states ---- */
int host_state_size(void) { return g_loaded_game ? (int)core.retro_serialize_size() : 0; }

int host_save_state(const char *path)
{
    if (!g_loaded_game) return 0;
    size_t sz = core.retro_serialize_size();
    if (!sz) return 0;
    uint8_t *buf = (uint8_t *)malloc(sz);
    if (!buf) return 0;
    int ok = core.retro_serialize(buf, sz) ? 1 : 0;
    if (ok) {
        char tmp[1100];
        snprintf(tmp, sizeof(tmp), "%s.tmp", path);
        FILE *f = fopen(tmp, "wb");
        if (!f) ok = 0;
        else {
            size_t n = fwrite(buf, 1, sz, f);
            ok = (fclose(f) == 0) && n == sz;
            if (ok) ok = rename(tmp, path) == 0;
            if (!ok) remove(tmp);
        }
    }
    free(buf);
    return ok;
}

int host_load_state(const char *path)
{
    if (!g_loaded_game) return 0;
    FILE *f = fopen(path, "rb");
    if (!f) return 0;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 0) { fclose(f); return 0; }
    uint8_t *buf = (uint8_t *)malloc((size_t)sz);
    if (!buf) { fclose(f); return 0; }
    int ok = fread(buf, 1, (size_t)sz, f) == (size_t)sz;
    fclose(f);
    if (ok) ok = core.retro_unserialize(buf, (size_t)sz) ? 1 : 0;
    free(buf);
    if (ok) { g_rw_cnt = 0; g_rw_head = 0; }
    return ok;
}

/* ---- cheats ---- */
void host_cheat_reset(void)
{
    if (g_loaded_core) core.retro_cheat_reset();
}

void host_cheat_set(unsigned index, int enabled, const char *code)
{
    if (g_loaded_game && code) core.retro_cheat_set(index, enabled != 0, code);
}

void host_unload(void)
{
    if (g_loaded_game) core.retro_unload_game();
    if (g_loaded_core) core.retro_deinit();
    if (core.handle) dlclose(core.handle);
    memset(&core, 0, sizeof(core));
    g_loaded_game = g_loaded_core = 0;
    free(g_game_data);
    g_game_data = NULL;
    free(g_rgba);
    g_rgba = NULL;
    g_rgba_cap = 0;
    g_w = g_h = 0;
    for (int i = 0; i < 2; i++) {
        free(g_mem[i].shadow);
        g_mem[i].shadow = NULL;
        g_mem[i].size = 0;
    }
    if (g_rw) {
        for (int i = 0; i < g_rw_cap; i++) free(g_rw[i]);
        free(g_rw);
    }
    g_rw = NULL;
    g_rw_cap = g_rw_cnt = g_rw_head = 0;
    g_rw_sz = 0;
    g_rw_on = 0;
    g_save_base[0] = 0;
}

int host_width(void) { return g_w; }
int host_height(void) { return g_h; }
float host_aspect(void) { return g_aspect > 0.f ? g_aspect : (g_h ? (float)g_w / (float)g_h : 1.f); }
double host_fps(void) { return g_fps; }
double host_sample_rate(void) { return g_rate; }
int host_frame_count(void) { return g_frames; }

void host_copy_rgba(uint32_t *dst, int dst_stride_px)
{
    if (!g_rgba || !dst) return;
    for (int y = 0; y < g_h; y++)
        memcpy(dst + (size_t)y * dst_stride_px, g_rgba + (size_t)y * g_w, (size_t)g_w * 4);
}

int host_take_audio(int16_t *dst, int max_frames)
{
    int n = g_audio_frames < max_frames ? g_audio_frames : max_frames;
    if (n > 0) {
        memcpy(dst, g_audio, (size_t)n * 4);
        if (n < g_audio_frames)
            memmove(g_audio, g_audio + (size_t)n * 2, (size_t)(g_audio_frames - n) * 4);
        g_audio_frames -= n;
    }
    return n;
}
