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
