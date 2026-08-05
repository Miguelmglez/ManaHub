export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, If-None-Match',
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method Not Allowed', { status: 405, headers: corsHeaders });
    }

    // ── Voice model downloads ──────────────────────────────────────────────
    // GET /voice/models/{lang}.zip  (also HEAD for pre-flight size checks)
    // Android VoiceModelRepositoryImpl downloads from this endpoint.
    const voiceMatch = path.match(/^\/voice\/models\/([a-z]{2})\.zip$/);
    if (voiceMatch) {
      const lang = voiceMatch[1];
      const object = await env.MANAHUB_ASSETS.get(`voice-models/${lang}.zip`);

      if (object === null) {
        return new Response(JSON.stringify({ error: 'Model not found' }), {
          status: 404,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      const headers = new Headers(corsHeaders);
      headers.set('Content-Type', 'application/zip');
      headers.set('Cache-Control', 'public, max-age=86400');
      headers.set('Content-Length', object.size.toString());
      if (object.httpEtag) headers.set('ETag', object.httpEtag);

      // HEAD: return metadata only, no body
      if (request.method === 'HEAD') {
        return new Response(null, { status: 200, headers });
      }

      return new Response(object.body, { status: 200, headers });
    }

    // ── Daily Puzzle ──────────────────────────────────────────────────────
    // Private R2 prefix `puzzle/` (distinct from the public `draft/` prefix):
    //   puzzle/{date}.json   — one puzzle document per day, date = yyyy-MM-dd
    //   puzzle/index.json    — { schemaVersion, entries: [{date,type}], answerHistory: [{date,normalizedName}] }
    //     answerHistory is for the generator's ~90-day dedup pass ONLY (fetched by the generator
    //     directly via `wrangler r2 object get`, never through this Worker) — the public
    //     /puzzle/index route below strips it before responding.
    //
    // Rollover boundary is 00:00 UTC, computed fresh per request. This is a PERMANENT constant —
    // changing it after puzzles are published would split a day's leaderboard/streak data in two.
    // Never make it configurable or timezone-aware.
    if (path === '/puzzle/today' || path === '/puzzle/index' || /^\/puzzle\/\d{4}-\d{2}-\d{2}$/.test(path)) {
      const todayUtc = new Date().toISOString().slice(0, 10);

      if (path === '/puzzle/index') {
        const indexObject = await env.MANAHUB_ASSETS.get('puzzle/index.json');
        if (indexObject === null) {
          return new Response(JSON.stringify({ schemaVersion: 1, entries: [] }), {
            status: 200,
            headers: { ...corsHeaders, 'Content-Type': 'application/json; charset=utf-8' },
          });
        }
        let indexData;
        try {
          indexData = JSON.parse(await indexObject.text());
        } catch (e) {
          return new Response(JSON.stringify({ error: 'Corrupt index' }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
        // Never leak a future date's existence, and never expose answerHistory to clients —
        // that field would let a client read every past puzzle's answer for zero legitimate reason.
        const filtered = {
          schemaVersion: indexData.schemaVersion ?? 1,
          entries: Array.isArray(indexData.entries)
            ? indexData.entries.filter((e) => e && typeof e.date === 'string' && e.date <= todayUtc)
            : [],
        };
        return new Response(JSON.stringify(filtered), {
          status: 200,
          headers: {
            ...corsHeaders,
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'public, max-age=300, stale-while-revalidate=60',
          },
        });
      }

      let dateKey;
      if (path === '/puzzle/today') {
        dateKey = todayUtc;
      } else {
        const requestedDate = path.slice('/puzzle/'.length);
        // Hard guard — the single most important line in this route. A future-dated puzzle must
        // NEVER be servable even if the generator already uploaded it (batches publish up to
        // ~30 days ahead). Evaluated before any R2 read.
        if (requestedDate > todayUtc) {
          return new Response(JSON.stringify({ error: 'Not Found' }), {
            status: 404,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }
        dateKey = requestedDate;
      }

      const puzzleIfNoneMatch = request.headers.get('If-None-Match');
      const puzzleObject = await env.MANAHUB_ASSETS.get(`puzzle/${dateKey}.json`, {
        onlyIf: puzzleIfNoneMatch ? { etagDoesNotMatch: puzzleIfNoneMatch } : undefined,
      });

      if (puzzleObject === null) {
        return new Response(JSON.stringify({ error: 'No puzzle published for this date' }), {
          status: 404,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // R2 returns a body-less object when ETag matches → 304
      if (!puzzleObject.body) {
        return new Response(null, {
          status: 304,
          headers: { ...corsHeaders, ETag: puzzleObject.httpEtag ?? '' },
        });
      }

      const puzzleHeaders = new Headers(corsHeaders);
      puzzleHeaders.set('Content-Type', 'application/json; charset=utf-8');
      // /today is time-sensitive around the rollover boundary; a specific past date is immutable
      // forever once published, so it can be cached hard.
      puzzleHeaders.set(
        'Cache-Control',
        path === '/puzzle/today'
          ? 'public, max-age=60, stale-while-revalidate=300'
          : 'public, max-age=31536000, immutable',
      );
      if (puzzleObject.httpEtag) puzzleHeaders.set('ETag', puzzleObject.httpEtag);

      return new Response(puzzleObject.body, { headers: puzzleHeaders });
    }

    // ── Draft content ──────────────────────────────────────────────────────
    // Allowed paths:
    //   /draft/sets-index.json
    //   /draft/{setCode}/guide.json
    //   /draft/{setCode}/tier-list.json
    //   /draft/{setCode}/booster.json
    //   /draft/{setCode}/engine.json
    const match = path.match(/^\/draft\/(sets-index\.json|[a-z]{2,6}\/(guide|tier-list|booster|engine)\.json)$/);
    if (!match) {
      return new Response(JSON.stringify({ error: 'Not Found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const key = 'draft/' + match[1];

    const ifNoneMatch = request.headers.get('If-None-Match');
    const object = await env.MANAHUB_ASSETS.get(key, {
      onlyIf: ifNoneMatch ? { etagDoesNotMatch: ifNoneMatch } : undefined,
    });

    if (object === null) {
      return new Response(JSON.stringify({ error: 'Content not found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // R2 returns a body-less object when ETag matches → 304
    if (!object.body) {
      return new Response(null, {
        status: 304,
        headers: { ...corsHeaders, ETag: object.httpEtag ?? '' },
      });
    }

    const headers = new Headers(corsHeaders);
    headers.set('Content-Type', 'application/json; charset=utf-8');
    headers.set('Cache-Control', 'public, max-age=300, stale-while-revalidate=60');
    if (object.httpEtag) headers.set('ETag', object.httpEtag);

    return new Response(object.body, { headers });
  },
};
