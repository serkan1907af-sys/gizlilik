/* Desktop smoke test for host.c: runs a core for N frames, writes a screenshot (PPM)
 * and reports whether video/audio/input behave.
 * usage: test_host core.so system_dir save_dir game_file out.ppm [frames] [device] */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../frontend/host.h"

int main(int argc, char **argv)
{
    if (argc < 6) {
        fprintf(stderr, "usage: %s core sys save game out.ppm [frames] [device]\n", argv[0]);
        return 2;
    }
    int frames = argc > 6 ? atoi(argv[6]) : 300;
    unsigned device = argc > 7 ? (unsigned)atoi(argv[7]) : 0;

    if (!host_load_core(argv[1], argv[2], argv[3])) { printf("FAIL load core: %s\n", host_last_error()); return 1; }
    if (!host_load_game(argv[4], device)) { printf("FAIL load game: %s\n", host_last_error()); return 1; }

    static int16_t abuf[8192 * 2];
    long audio_frames = 0, nonzero = 0;
    for (int i = 0; i < frames; i++) {
        /* press START at frames 60-70 and 120-130 to get past title / into menu */
        uint32_t btn = 0;
        if ((i > 60 && i < 70) || (i > 120 && i < 130)) btn |= 1u << 3;
        host_set_input(btn, 0, 0, 0, 0);
        host_run();
        int n;
        while ((n = host_take_audio(abuf, 8192)) > 0) {
            audio_frames += n;
            for (int k = 0; k < n * 2; k++) if (abuf[k]) nonzero++;
        }
    }

    int w = host_width(), h = host_height();
    printf("frames_rendered=%d size=%dx%d aspect=%.3f fps=%.2f rate=%.0f audio_frames=%ld nonzero_samples=%ld\n",
           host_frame_count(), w, h, host_aspect(), host_fps(), host_sample_rate(), audio_frames, nonzero);
    if (w <= 0 || h <= 0) { printf("FAIL no video\n"); return 1; }

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
    printf("non_black_pixels=%ld of %d\n", colored, w * h);
    host_unload();
    printf("OK\n");
    return colored > 100 ? 0 : 1;
}
