export default function Badge({ text, className }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>{text}</span>
}
