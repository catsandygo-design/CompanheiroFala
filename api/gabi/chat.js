import { GoogleGenerativeAI } from "@google/generative-ai";

export const config = { maxDuration: 60 };

/**
 * POST /api/gabi/chat
 * Entrada Android: { "message": "..." }. A chave Gemini fica apenas no Vercel.
 * Saída: SSE data: {"text":"..."}, seguido de data: [DONE].
 */
export default async function handler(request, response) {
  if (request.method !== "POST") {
    response.setHeader("Allow", "POST");
    return response.status(405).json({ error: "Método não permitido" });
  }

  const prompt = typeof request.body?.message === "string"
    ? request.body.message.trim()
    : typeof request.body?.prompt === "string"
      ? request.body.prompt.trim()
      : "";

  if (!prompt) return response.status(400).json({ error: "Mensagem obrigatória" });
  if (prompt.length > 6000) return response.status(413).json({ error: "Mensagem muito longa" });
  if (!process.env.GEMINI_API_KEY) {
    return response.status(503).json({ error: "GEMINI_API_KEY não configurada no Vercel" });
  }

  try {
    const client = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    // Uma conversa de voz infantil precisa priorizar a primeira resposta, não raciocínio longo.
    // Flash-Lite é o modelo de menor latência. Não usamos GEMINI_MODEL aqui porque uma
    // variável antiga no Vercel pode apontar para uma versão desativada do Gemini.
    const model = client.getGenerativeModel({
      model: "gemini-3.5-flash-lite",
      generationConfig: {
        maxOutputTokens: 60,
        temperature: 0.55,
      },
    });

    // Abre o SSE antes de solicitar o modelo: o cliente confirma a conexão imediatamente.
    response.statusCode = 200;
    response.setHeader("Content-Type", "text/event-stream; charset=utf-8");
    response.setHeader("Cache-Control", "no-cache, no-transform");
    response.setHeader("Connection", "keep-alive");
    response.flushHeaders?.();
    response.write(": connected\n\n");
    const result = await model.generateContentStream(prompt);

    for await (const chunk of result.stream) {
      const text = chunk.text();
      if (text) response.write(`data: ${JSON.stringify({ text })}\n\n`);
    }
    response.write("data: [DONE]\n\n");
    response.end();
  } catch (error) {
    const message = error instanceof Error ? error.message : "Falha ao gerar resposta Gemini";
    if (response.headersSent) {
      response.write(`event: error\ndata: ${JSON.stringify({ error: message })}\n\n`);
      return response.end();
    }
    return response.status(502).json({ error: message });
  }
}
