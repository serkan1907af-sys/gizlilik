/* Desktop smoke test for host.c: video, audio, battery save, save state, rewind, cheats.
 * usage: test_host core.so system_dir save_dir game_file out.ppm [frames] */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include "../frontend/host.h"

static int fails = 0;
#define CHECK(cond, msg) do { if (!(cond)) { printf("FAIL: %s\n", msg); fails++; } else printf("ok:   %s\n", msg); } while (0)

static long fsize(const char *p) { struct stat st; return stat(p, &st) == 0 ? (long)st.st_size : -1; }

static uint32_t checksum(void)
{
    int w = host_width(), h = host_height();
    uint32_t *px = (uint32_t *)malloc((size_t)w * h * 4);
    host_copy_rgba(px, w);
    uint32_t s = 2166136261u;
    for (int i = 0; i < w * h; i++) { s ^= px[i]; s *= 16777619u; }
    free(px);
    return s;
}

int main(int argc, char **argv)
{
    if (argc < 6) { fprintf(stderr, "usage: %s core sys save game out.ppm [frames]\n", argv[0]); return 2; }
    int frames = argc > 6 ? atoi(argv[6]) : 200;
    char base[1024], state[1100], srm[1100];
    snprintf(base, sizeof(base), "%s/test", argv[3]);
    snprintf(state, sizeof(state), "%s/test.state", argv[3]);
    snprintf(srm, sizeof(srm), "%s.srm", base);
    remove(state);
    remove(srm);

    host_set_option("gambatte_gb_colorization", "auto");
    CHECK(host_load_core(argv[1], argv[2], argv[3]), "core loads");
    CHECK(host_load_game(argv[4]), "game loads");
    host_load_saves(base);
    host_rewind_enable(32, 2);

    static int16_t abuf[8192 * 2];
    long audio_frames = 0;
    for (int i = 0; i < frames; i++) {
        host_set_input((i > 60 && i < 70) ? (1u << 3) : 0);
        host_run();
        int n;
        while ((n = host_take_audio(abuf, 8192)) > 0) audio_frames += n;
    }
    int w = host_width(), h = host_height();
    printf("info: %dx%d aspect=%.3f fps=%.2f rate=%.0f frames=%d audio=%ld statesize=%d\n",
           w, h, host_aspect(), host_fps(), host_sample_rate(), host_frame_count(), audio_frames, host_state_size());
    CHECK(w > 0 && h > 0, "video size > 0");
    CHECK(audio_frames > 1000, "audio produced");
    CHECK(host_state_size() > 0, "serialize size > 0");

    /* screenshot + colored pixel count */
    {
        uint32_t *px = (uint32_t *)malloc((size_t)w * h * 4);
        host_copy_rgba(px, w);
        long colored = 0;
        FILE *f = fopen(argv[5], "wb");
        fprintf(f, "P6\n%d %d\n255\n", w, h);
        for (int i = 0; i < w * h; i++) {
            uint8_t *b = (uint8_t *)&px[i];
            fwrite(b, 1, 3, f);
            if (b[0] | b[1] | b[2]) colored++;
        }
        fclose(f);
        free(px);
        CHECK(colored > 100, "frame is not black");
    }

    /* save state -> run more -> load state restores */
    CHECK(host_save_state(state), "save state");
    CHECK(fsize(state) > 0, "state file written");
    uint32_t before = checksum();
    for (int i = 0; i < 40; i++) { host_set_input(1u << 7); host_run(); }
    CHECK(host_load_state(state), "load state");
    host_run();
    printf("info: checksum before=%08x after-load+1frame=%08x\n", before, checksum());

    /* rewind */
    for (int i = 0; i < 60; i++) { host_set_input(0); host_run(); }
    int cnt = host_rewind_count();
    printf("info: rewind snapshots=%d\n", cnt);
    CHECK(cnt > 5, "rewind has snapshots");
    int steps = 0;
    while (steps < 10 && host_rewind_step()) steps++;
    CHECK(steps == 10, "rewind steps back 10 times");
    CHECK(host_rewind_count() == cnt - 10, "rewind count decreases");

    /* cheats must not crash */
    host_cheat_reset();
    host_cheat_set(0, 1, "SXIOPO");
    host_cheat_set(0, 0, "SXIOPO");
    host_run();
    printf("ok:   cheat calls survived\n");

    /* battery save */
    int wrote = host_flush_saves();
    printf("info: flush wrote %d file(s), srm size=%ld\n", wrote, fsize(srm));
    host_unload();
    printf(fails ? "RESULT: %d FAILED\n" : "RESULT: ALL OK (%d)\n", fails);
    return fails ? 1 : 0;
}
