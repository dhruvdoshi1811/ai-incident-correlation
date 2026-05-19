function renderInline(text, keyPrefix) {
  return text
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((part, i) =>
      part.startsWith('**') && part.endsWith('**') ? (
        <strong key={`${keyPrefix}-${i}`}>{part.slice(2, -2)}</strong>
      ) : (
        <span key={`${keyPrefix}-${i}`}>{part}</span>
      ),
    )
}

function groupLines(lines) {
  const blocks = []
  let currentList = null
  for (const line of lines) {
    const trimmed = line.trim()
    const isBullet = trimmed.startsWith('- ') || trimmed.startsWith('* ')
    if (isBullet) {
      if (!currentList) {
        currentList = []
        blocks.push({ type: 'list', items: currentList })
      }
      currentList.push(trimmed.slice(2))
    } else {
      currentList = null
      if (trimmed.length > 0) {
        blocks.push({ type: 'paragraph', text: trimmed })
      }
    }
  }
  return blocks
}

export default function Markdown({ text }) {
  if (!text) return null

  return (
    <div className="space-y-2">
      {text.split(/\n{2,}/).flatMap((chunk, chunkIndex) =>
        groupLines(chunk.split('\n')).map((block, blockIndex) => {
          const key = `${chunkIndex}-${blockIndex}`
          if (block.type === 'list') {
            return (
              <ul key={key} className="list-disc space-y-1 pl-5">
                {block.items.map((item, i) => (
                  <li key={i}>{renderInline(item, `${key}-${i}`)}</li>
                ))}
              </ul>
            )
          }
          return <p key={key}>{renderInline(block.text, key)}</p>
        }),
      )}
    </div>
  )
}
