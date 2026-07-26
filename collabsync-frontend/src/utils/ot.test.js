import { describe, it, expect } from 'vitest'
import {
  applyOperation,
  transformOperation,
  transformPendingOps,
  composeOperations,
  adjustCursorPosition,
  diff,
  getCursorCoordinates,
  getUserColor,
  OpType
} from '../utils/ot'

describe('OT (Operational Transformation) Utilities', () => {
  describe('applyOperation', () => {
    it('applies INSERT operation at beginning', () => {
      const result = applyOperation('', { opType: OpType.INSERT, position: 0, content: 'Hello' })
      expect(result).toBe('Hello')
    })

    it('applies INSERT operation in middle', () => {
      const result = applyOperation('Hello World', { opType: OpType.INSERT, position: 5, content: ', ' })
      expect(result).toBe('Hello,  World')
    })

    it('applies INSERT operation at end', () => {
      const result = applyOperation('Hello', { opType: OpType.INSERT, position: 5, content: ' World' })
      expect(result).toBe('Hello World')
    })

    it('applies DELETE operation from beginning', () => {
      const result = applyOperation('Hello World', { opType: OpType.DELETE, position: 0, length: 6 })
      expect(result).toBe('World')
    })

    it('applies DELETE operation in middle', () => {
      const result = applyOperation('Hello World', { opType: OpType.DELETE, position: 5, length: 1 })
      expect(result).toBe('HelloWorld')
    })

    it('applies DELETE operation at end', () => {
      const result = applyOperation('Hello World', { opType: OpType.DELETE, position: 5, length: 6 })
      expect(result).toBe('Hello')
    })

    it('handles position beyond text length', () => {
      const result = applyOperation('Hi', { opType: OpType.INSERT, position: 10, content: ' there' })
      expect(result).toBe('Hi there')
    })

    it('handles empty text', () => {
      const result = applyOperation('', { opType: OpType.INSERT, position: 0, content: 'Test' })
      expect(result).toBe('Test')
    })
  })

  describe('transformOperation', () => {
    describe('INSERT vs INSERT', () => {
      it('shifts position when remote insert is before local insert', () => {
        const local = { opType: OpType.INSERT, position: 10, content: 'local' }
        const remote = { opType: OpType.INSERT, position: 5, content: 'remote' }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(10 + 6)
      })

      it('shifts position when remote insert is at same position (tie-break)', () => {
        const local = { opType: OpType.INSERT, position: 5, content: 'local' }
        const remote = { opType: OpType.INSERT, position: 5, content: 'remote' }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(5 + 6)
      })

      it('does not shift when remote insert is after local insert', () => {
        const local = { opType: OpType.INSERT, position: 5, content: 'local' }
        const remote = { opType: OpType.INSERT, position: 10, content: 'remote' }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(5)
      })
    })

    describe('INSERT vs DELETE', () => {
      it('adjusts insert position when remote delete is before', () => {
        const local = { opType: OpType.INSERT, position: 10, content: 'local' }
        const remote = { opType: OpType.DELETE, position: 3, length: 4 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(Math.max(3, 10 - 4))
      })

      it('does not adjust when remote delete is after insert', () => {
        const local = { opType: OpType.INSERT, position: 5, content: 'local' }
        const remote = { opType: OpType.DELETE, position: 10, length: 4 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(5)
      })
    })

    describe('DELETE vs INSERT', () => {
      it('shifts delete position when remote insert is before', () => {
        const local = { opType: OpType.DELETE, position: 10, length: 3 }
        const remote = { opType: OpType.INSERT, position: 5, content: 'remote' }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(10 + 6)
      })

      it('extends delete length when remote insert is within delete range', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 10 }
        const remote = { opType: OpType.INSERT, position: 8, content: 'x' }
        const transformed = transformOperation(local, remote)
        expect(transformed.length).toBe(10 + 1)
      })

      it('does not change when remote insert is after delete range', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 3 }
        const remote = { opType: OpType.INSERT, position: 10, content: 'x' }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(5)
        expect(transformed.length).toBe(3)
      })
    })

    describe('DELETE vs DELETE', () => {
      it('shifts position when remote delete is entirely before', () => {
        const local = { opType: OpType.DELETE, position: 10, length: 5 }
        const remote = { opType: OpType.DELETE, position: 2, length: 3 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(10 - 3)
      })

      it('no change when remote delete is entirely after', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 3 }
        const remote = { opType: OpType.DELETE, position: 15, length: 3 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(5)
        expect(transformed.length).toBe(3)
      })

      it('handles remote delete completely covering local delete', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 5 }
        const remote = { opType: OpType.DELETE, position: 3, length: 10 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(3)
        expect(transformed.length).toBe(0)
      })

      it('handles remote delete overlapping start of local delete', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 10 }
        const remote = { opType: OpType.DELETE, position: 3, length: 4 }
        const transformed = transformOperation(local, remote)
        expect(transformed.position).toBe(3)
        expect(transformed.length).toBe(15 - 7)
      })

      it('handles remote delete overlapping end of local delete', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 10 }
        const remote = { opType: OpType.DELETE, position: 12, length: 5 }
        const transformed = transformOperation(local, remote)
        expect(transformed.length).toBe(12 - 5)
      })

      it('handles remote delete in middle of local delete', () => {
        const local = { opType: OpType.DELETE, position: 5, length: 10 }
        const remote = { opType: OpType.DELETE, position: 8, length: 3 }
        const transformed = transformOperation(local, remote)
        expect(transformed.length).toBe(10 - 3)
      })
    })
  })

  describe('transformPendingOps', () => {
    it('transforms multiple pending operations against a remote operation', () => {
      const pending = [
        { opType: OpType.INSERT, position: 10, content: 'a' },
        { opType: OpType.DELETE, position: 15, length: 3 }
      ]
      const remote = { opType: OpType.INSERT, position: 5, content: 'remote' }

      const transformed = transformPendingOps(pending, remote)
      expect(transformed[0].position).toBe(10 + 6)
      expect(transformed[1].position).toBe(15 + 6)
    })

    it('returns empty array for empty pending ops', () => {
      const transformed = transformPendingOps([], { opType: OpType.INSERT, position: 0, content: 'x' })
      expect(transformed).toEqual([])
    })
  })

  describe('composeOperations', () => {
    it('composes adjacent inserts at same position', () => {
      const opA = { opType: OpType.INSERT, position: 5, content: 'Hello' }
      const opB = { opType: OpType.INSERT, position: 5, content: ' World' }
      const composed = composeOperations(opA, opB)
      expect(composed).not.toBeNull()
      expect(composed.content).toBe('Hello World')
    })

    it('composes adjacent inserts where B follows A', () => {
      const opA = { opType: OpType.INSERT, position: 5, content: 'Hello' }
      const opB = { opType: OpType.INSERT, position: 10, content: ' World' }
      const composed = composeOperations(opA, opB)
      expect(composed).not.toBeNull()
      expect(composed.content).toBe('Hello World')
    })

    it('composes adjacent deletes at same position', () => {
      const opA = { opType: OpType.DELETE, position: 5, length: 3 }
      const opB = { opType: OpType.DELETE, position: 5, length: 2 }
      const composed = composeOperations(opA, opB)
      expect(composed).not.toBeNull()
      expect(composed.length).toBe(5)
    })

    it('returns null for different operation types', () => {
      const opA = { opType: OpType.INSERT, position: 5, content: 'a' }
      const opB = { opType: OpType.DELETE, position: 5, length: 1 }
      const composed = composeOperations(opA, opB)
      expect(composed).toBeNull()
    })

    it('returns null for non-adjacent operations', () => {
      const opA = { opType: OpType.INSERT, position: 5, content: 'a' }
      const opB = { opType: OpType.INSERT, position: 20, content: 'b' }
      const composed = composeOperations(opA, opB)
      expect(composed).toBeNull()
    })
  })

  describe('adjustCursorPosition', () => {
    it('shifts cursor right when remote insert is before cursor', () => {
      const newPos = adjustCursorPosition(10, { opType: OpType.INSERT, position: 5, content: 'Hello' })
      expect(newPos).toBe(10 + 5)
    })

    it('shifts cursor right when remote insert is at cursor (tie-break)', () => {
      const newPos = adjustCursorPosition(5, { opType: OpType.INSERT, position: 5, content: 'Hello' })
      expect(newPos).toBe(5 + 5)
    })

    it('does not shift cursor when remote insert is after cursor', () => {
      const newPos = adjustCursorPosition(5, { opType: OpType.INSERT, position: 10, content: 'Hello' })
      expect(newPos).toBe(5)
    })

    it('shifts cursor left when remote delete is entirely before cursor', () => {
      const newPos = adjustCursorPosition(15, { opType: OpType.DELETE, position: 5, length: 3 })
      expect(newPos).toBe(15 - 3)
    })

    it('moves cursor to delete start when remote delete overlaps cursor', () => {
      const newPos = adjustCursorPosition(10, { opType: OpType.DELETE, position: 5, length: 10 })
      expect(newPos).toBe(5)
    })

    it('does not change cursor when remote delete is after cursor', () => {
      const newPos = adjustCursorPosition(5, { opType: OpType.DELETE, position: 10, length: 3 })
      expect(newPos).toBe(5)
    })
  })

  describe('diff', () => {
    it('detects insert at beginning', () => {
      const op = diff('', 'Hello', 5)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.INSERT)
      expect(op.position).toBe(0)
      expect(op.content).toBe('Hello')
    })

    it('detects insert in middle', () => {
      const op = diff('Hello World', 'Hello, World', 6)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.INSERT)
      expect(op.position).toBe(5)
      expect(op.content).toBe(',')
    })

    it('detects insert at end', () => {
      const op = diff('Hello', 'Hello World', 11)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.INSERT)
      expect(op.position).toBe(5)
      expect(op.content).toBe(' World')
    })

    it('detects delete from beginning', () => {
      const op = diff('Hello World', 'World', 0)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.DELETE)
      expect(op.position).toBe(0)
      expect(op.length).toBe(6)
    })

    it('detects delete in middle', () => {
      const op = diff('Hello World', 'HelloWorld', 5)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.DELETE)
      expect(op.position).toBe(5)
      expect(op.length).toBe(1)
    })

    it('detects delete at end', () => {
      const op = diff('Hello World', 'Hello', 5)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.DELETE)
      expect(op.position).toBe(5)
      expect(op.length).toBe(6)
    })

    it('returns null for no change', () => {
      const op = diff('Hello', 'Hello', 5)
      expect(op).toBeNull()
    })

    it('handles replace as delete (returns delete first)', () => {
      const op = diff('Hello World', 'Hello There', 11)
      expect(op).not.toBeNull()
      expect(op.opType).toBe(OpType.DELETE)
      expect(op.position).toBe(6)
      expect(op.length).toBe(5)
    })
  })

  describe('getCursorCoordinates', () => {
    it('returns correct position for start of text', () => {
      const mockTextarea = { value: '' }
      const coords = getCursorCoordinates(mockTextarea, 0)
      expect(coords.x).toBe(16)
      expect(coords.y).toBe(16)
    })

    it('returns correct position for middle of line', () => {
      const mockTextarea = { value: 'Hello World' }
      const coords = getCursorCoordinates(mockTextarea, 5)
      expect(coords.x).toBe(5 * 8.4 + 16)
      expect(coords.y).toBe(16)
    })

    it('returns correct position for second line', () => {
      const mockTextarea = { value: 'Line 1\nLine 2' }
      const coords = getCursorCoordinates(mockTextarea, 7) // Start of "Line 2"
      expect(coords.x).toBe(16)
      expect(coords.y).toBe(22 + 16)
    })

    it('handles null textarea', () => {
      const coords = getCursorCoordinates(null, 5)
      expect(coords.x).toBe(0)
      expect(coords.y).toBe(0)
    })
  })

  describe('getUserColor', () => {
    it('returns consistent color for same user ID', () => {
      const color1 = getUserColor('user123')
      const color2 = getUserColor('user123')
      expect(color1).toBe(color2)
    })

    it('returns colors from predefined palette', () => {
      const colors = new Set()
      for (let i = 0; i < 100; i++) {
        colors.add(getUserColor(`user${i}`))
      }
      // Should only use colors from the palette
      const palette = [
        '#ef4444', '#f97316', '#f59e0b', '#22c55e',
        '#06b6d4', '#3b82f6', '#8b5cf6', '#ec4899'
      ]
      colors.forEach(color => {
        expect(palette).toContain(color)
      })
    })
  })
})