import DOMPurify from "dompurify";

const allowedTags = [
  "a", "b", "strong", "i", "em", "u", "s", "del", "strike", "code", "pre", "blockquote",
  "ul", "ol", "li", "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "sup", "sub", "span", "hr",
];

const linkPattern = /\b((?:https?:\/\/|www\.)[^\s<>"']+[^\s<>"'.,;:!?)\]}])/gi;

DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A") {
    const href = node.getAttribute("href") ?? "";
    if (!/^(https?:|mailto:)/i.test(href)) node.removeAttribute("href");
    node.setAttribute("target", "_blank");
    node.setAttribute("rel", "noopener noreferrer nofollow");
  }
});

function escape(text: string): string {
  return text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

function linkify(text: string): string {
  return escape(text).replace(linkPattern, (url) => {
    const href = url.startsWith("www.") ? `https://${url}` : url;
    return `<a href="${href}">${url}</a>`;
  });
}

function stripReply(html: string): string {
  return html.replace(/<mx-reply>[\s\S]*?<\/mx-reply>/gi, "");
}

export function messageHtml(body: string, html?: string): string {
  const source = html ? stripReply(html) : linkify(body);
  return DOMPurify.sanitize(source, {
    ALLOWED_TAGS: allowedTags,
    ALLOWED_ATTR: ["href"],
    ALLOW_DATA_ATTR: false,
  });
}
