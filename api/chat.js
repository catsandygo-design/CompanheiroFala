const MAX_MESSAGE_LENGTH = 600

const COMPANION_INSTRUCTIONS = `Você é a Fadinha, uma companhia infantil acolhedora para uma criança pequena que fala português do Brasil.
Responda em no máximo duas frases curtas, com tom carinhoso, simples e apropriado para a idade.
Não peça dados pessoais, não dê diagnósticos, não trate emergências e não descreva conteúdo sexual, violento ou perigoso.
Se a criança relatar medo, dor, perigo, abuso, autoagressão ou quiser fazer algo perigoso, diga com calma para procurar imediatamente um adulto de confiança e mantenha a resposta curta.
Nunca incentive segredos com adultos nem peça para a criança sair de casa, usar medicamentos, acessar links ou fazer compras.
Quando a criança quiser brincar ou aprender, proponha uma atividade simples e segura.`

function reply(res, status, body) {
  res.status(status).json(body)
}

export default async function handler(req, res) {
  if (req.method === "GET") return reply(res, 200, { ok: true })
  if (req.method !== "POST") {
    res.setHeader("Allow", "GET, POST")
    return reply(res, 405, { error: "Método não permitido." })
  }

  if (!process.env.OPENAI_API_KEY) {
    return reply(res, 503, { error: "O serviço de conversa ainda não foi configurado." })
  }

  const message = typeof req.body?.message === "string" ? req.body.message.trim() : ""
  if (!message || message.length > MAX_MESSAGE_LENGTH) {
    return reply(res, 400, { error: "Envie uma mensagem de até 600 caracteres." })
  }

  try {
    const upstream = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || "gpt-5-mini",
        input: [
          { role: "developer", content: COMPANION_INSTRUCTIONS },
          { role: "user", content: message }
        ],
        store: false,
        max_output_tokens: 180
      })
    })
    const data = await upstream.json()
    if (!upstream.ok) {
      console.error("OpenAI request failed", upstream.status, data?.error?.type)
      return reply(res, 502, { error: "Não consegui conversar agora. Vamos tentar de novo?" })
    }

    const text = typeof data.output_text === "string" ? data.output_text.trim() : ""
    if (!text) return reply(res, 502, { error: "Não consegui preparar uma resposta agora." })
    return reply(res, 200, { reply: text })
  } catch (error) {
    console.error("Chat function failed", error?.name)
    return reply(res, 502, { error: "Não consegui conversar agora. Vamos tentar de novo?" })
  }
}
