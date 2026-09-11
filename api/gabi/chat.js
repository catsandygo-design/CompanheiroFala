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
      systemInstruction: {
        role: "system",
        parts: [{ text: `Você é Lumi, companheira carinhosa de uma criança brasileira de cinco anos.
Converse naturalmente em português do Brasil, em no máximo duas frases curtas.

Além da conversa, ofereça reforço lúdico de linguagem, nunca tratamento fonoaudiológico:
- Primeiro responda ao significado, à emoção ou à intenção da criança.
- Em alguns momentos apropriados, modele naturalmente uma palavra ou uma frase mais clara; por exemplo, diga a forma correta dentro da sua resposta, sem falar que ela errou.
- Faça no máximo uma modelagem por resposta e apenas quando ela for simples, relevante e encorajadora.
- Não peça repetição obrigatória, não dê notas, não compare a criança e não corrija sotaque, regionalismo ou nome próprio.
- Se a criança estiver triste, com medo, relatando briga, dor, agressão ou outro assunto sensível, acolha primeiro e não faça exercício de fala.
- Para brincadeiras de fala pedidas pela criança, proponha uma opção curta e divertida, como rima, som inicial ou completar uma frase.
- Não diagnostique, não prescreva terapia e não substitua fonoaudiólogo ou pediatra.` }],
      },
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
