// OT (Operational Transformation) utilities for collaborative editing
// These are pure functions extracted from EditorPage for unit testing

/**
 * Operation types for text editing
 */
export const OpType = {
  INSERT: 'INSERT',
  DELETE: 'DELETE'
}

/**
 * Apply an operation to a text string
 * @param {string} text - The current text
 * @param {Object} operation - The operation to apply { opType, position, content, length }
 * @returns {string} - The new text after applying the operation
 */
export function applyOperation(text, operation) {
  if (!text) text = ''

  if (operation.opType === OpType.INSERT) {
    const pos = Math.min(operation.position, text.length)
    return text.slice(0, pos) + (operation.content || '') + text.slice(pos)
  } else if (operation.opType === OpType.DELETE) {
    const pos = Math.min(operation.position, text.length)
    const end = Math.min(pos + (operation.length || 0), text.length)
    return text.slice(0, pos) + text.slice(end)
  }
  return text
}

/**
 * Transform operation A against operation B (B happened first)
 * Returns the transformed version of A that can be applied after B
 * @param {Object} opA - The operation to transform
 * @param {Object} opB - The operation that happened first
 * @returns {Object} - Transformed operation A
 */
export function transformOperation(opA, opB) {
  // Clone to avoid mutation
  const result = { ...opA }

  if (opA.opType === OpType.INSERT && opB.opType === OpType.INSERT) {
    // Insert vs Insert
    if (opB.position <= opA.position) {
      result.position = opA.position + (opB.content?.length || 0)
    } else if (opB.position === opA.position) {
      // Tie-break: opB wins (earlier serverSeq)
      result.position = opA.position + (opB.content?.length || 0)
    }
  } else if (opA.opType === OpType.INSERT && opB.opType === OpType.DELETE) {
    // Insert vs Delete
    if (opB.position <= opA.position) {
      result.position = Math.max(opB.position, opA.position - (opB.length || 0))
    }
  } else if (opA.opType === OpType.DELETE && opB.opType === OpType.INSERT) {
    // Delete vs Insert
    if (opB.position <= opA.position) {
      result.position = opA.position + (opB.content?.length || 0)
    } else if (opB.position < opA.position + (opA.length || 0)) {
      result.length = (opA.length || 0) + (opB.content?.length || 0)
    }
  } else if (opA.opType === OpType.DELETE && opB.opType === OpType.DELETE) {
    // Delete vs Delete
    const opAEnd = opA.position + (opA.length || 0)
    const opBEnd = opB.position + (opB.length || 0)

    if (opBEnd <= opA.position) {
      // opB entirely before opA
      result.position = opA.position - (opB.length || 0)
    } else if (opB.position >= opAEnd) {
      // opB entirely after opA - no change needed
    } else if (opB.position <= opA.position && opBEnd >= opAEnd) {
      // opB completely covers opA
      result.position = opB.position
      result.length = 0
    } else if (opB.position <= opA.position) {
      // opB overlaps start of opA
      result.position = opB.position
      result.length = opAEnd - opBEnd
    } else if (opBEnd >= opAEnd) {
      // opB overlaps end of opA
      result.length = opB.position - opA.position
    } else {
      // opB in middle of opA - split (simplified: just reduce length)
      result.length = (opA.length || 0) - (opB.length || 0)
    }
  }

  return result
}

/**
 * Transform an array of pending operations against a new remote operation
 * @param {Array} pendingOps - Array of local pending operations
 * @param {Object} remoteOp - The remote operation to transform against
 * @returns {Array} - Transformed pending operations
 */
export function transformPendingOps(pendingOps, remoteOp) {
  return pendingOps.map(op => transformOperation(op, remoteOp))
}

/**
 * Compose two consecutive operations into one (when possible)
 * @param {Object} opA - First operation
 * @param {Object} opB - Second operation (happens after opA)
 * @returns {Object|null} - Composed operation or null if not composable
 */
export function composeOperations(opA, opB) {
  // Only compose if same type and adjacent
  if (opA.opType !== opB.opType) return null

  if (opA.opType === OpType.INSERT) {
    // Adjacent inserts at same position
    if (opB.position === opA.position + (opA.content?.length || 0)) {
      return {
        ...opA,
        content: (opA.content || '') + (opB.content || '')
      }
    }
    // Insert at same position (tie-break)
    if (opB.position === opA.position) {
      return {
        ...opA,
        content: (opA.content || '') + (opB.content || '')
      }
    }
  } else if (opA.opType === OpType.DELETE) {
    // Adjacent deletes
    if (opB.position === opA.position) {
      return {
        ...opA,
        length: (opA.length || 0) + (opB.length || 0)
      }
    }
  }
  return null
}

/**
 * Calculate cursor position after a remote operation is applied
 * @param {number} cursorPos - Current cursor position
 * @param {Object} remoteOp - The remote operation that was applied
 * @returns {number} - Adjusted cursor position
 */
export function adjustCursorPosition(cursorPos, remoteOp) {
  if (remoteOp.opType === OpType.INSERT) {
    if (remoteOp.position < cursorPos) {
      return cursorPos + (remoteOp.content?.length || 0)
    } else if (remoteOp.position === cursorPos) {
      // Tie-break: remote insert pushes cursor
      return cursorPos + (remoteOp.content?.length || 0)
    }
  } else if (remoteOp.opType === OpType.DELETE) {
    const remoteEnd = remoteOp.position + (remoteOp.length || 0)
    if (remoteEnd <= cursorPos) {
      return cursorPos - (remoteOp.length || 0)
    } else if (remoteOp.position < cursorPos) {
      return remoteOp.position
    }
  }
  return cursorPos
}

/**
 * Simple diff algorithm to extract operation from old/new text
 * @param {string} oldStr - Previous text
 * @param {string} newStr - New text
 * @param {number} cursorPos - Cursor position in new text
 * @returns {Object|null} - Operation or null if no change
 */
export function diff(oldStr, newStr, cursorPos) {
  let prefixLen = 0
  while (prefixLen < oldStr.length && prefixLen < newStr.length && oldStr[prefixLen] === newStr[prefixLen]) {
    prefixLen++
  }

  let suffixLen = 0
  while (
    suffixLen < oldStr.length - prefixLen &&
    suffixLen < newStr.length - prefixLen &&
    oldStr[oldStr.length - 1 - suffixLen] === newStr[newStr.length - 1 - suffixLen]
  ) {
    suffixLen++
  }

  const deletedLen = oldStr.length - prefixLen - suffixLen
  const insertedText = newStr.slice(prefixLen, newStr.length - suffixLen)

  if (deletedLen > 0 && insertedText.length > 0) {
    return { opType: OpType.DELETE, position: prefixLen, length: deletedLen }
  } else if (deletedLen > 0) {
    return { opType: OpType.DELETE, position: prefixLen, length: deletedLen }
  } else if (insertedText.length > 0) {
    return { opType: OpType.INSERT, position: prefixLen, content: insertedText }
  }
  return null
}

/**
 * Get cursor coordinates from textarea position
 * @param {HTMLTextAreaElement} textarea - The textarea element
 * @param {number} position - Character position
 * @returns {Object} - { x, y } coordinates
 */
export function getCursorCoordinates(textarea, position) {
  if (!textarea) return { x: 0, y: 0 }
  const text = textarea.value.substring(0, position)
  const lines = text.split('\n')
  const lineHeight = 22
  const charWidth = 8.4
  return {
    x: (lines[lines.length - 1].length * charWidth) + 16,
    y: ((lines.length - 1) * lineHeight) + 16
  }
}

/**
 * Generate a consistent color for a user ID
 * @param {string} userId - User identifier
 * @returns {string} - Hex color
 */
export function getUserColor(userId) {
  let hash = 0
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash)
  }
  const userColors = [
    '#ef4444', '#f97316', '#f59e0b', '#22c55e',
    '#06b6d4', '#3b82f6', '#8b5cf6', '#ec4899'
  ]
  return userColors[Math.abs(hash) % userColors.length]
}