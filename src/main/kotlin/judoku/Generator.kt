/*	Copyright (C) 2018 Steve Ball <jetpants@gmail.com>

	This file is part of Judoku. Judoku is free software: you can redistribute
	it and/or modify it under the terms of the GNU General Public License as
	published by the Free Software Foundation, either version 3 of the License,
	or (at your option) any later version.

    Judoku is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
    FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
    details.

    You should have received a copy of the GNU General Public License along with
    Judoku. If not, see <http://www.gnu.org/licenses/>.
*/

package judoku

import java.util.Random
import judoku.Grid

class Generator(private val prototype: Grid) {
    private var cached: Grid? = null

    init {
        check(prototype.isLegal())

        /*  generating solutions takes time. Although it's only used here for validation,
            don't throw away the result - we can use it later */
        val solutions = Solver.findSolutions(prototype, 1)

        require (solutions.size > 0) { "Prototype has no solutions" }

        cached = solutions[0]
    }

    enum class Symmetry {
        ROTATE180, DIAGONAL, HORIZONTAL, VERTICAL,
        RANDOM /*one of the above*/, NONE;          // these two must be last

        companion object {
            @JvmStatic
            fun toSymmetry(prefix: String): Symmetry? {
                var matches = 0
                var sym: Symmetry? = null

                for (v in Symmetry.values())
                    if (v.toString().startsWith(prefix.toUpperCase())) {
                        ++matches
                        sym = v
                    }

                return if (matches == 1) sym else null /*too many or too few*/
            }
        }
    }

    fun generate(_symmetry: Symmetry, _minimal: Boolean): Grid {
        // for these narrow boxes, no minimal solution will exist in some symmetry modes
        var minimal = _minimal
        if (prototype.boxWidth() == 1 || prototype.boxHeight() == 1)
            minimal = false

        var symmetry = derandomise(_symmetry)

        /*	if the symmetry mode is NONE (asymmetric) then it's always possible to generate a
		  	proper puzzle the first time and to do so quickly: you generate a complete
		  	solution with all cells filled and then inspect which givens can be removed while
		  	keeping the grid with only one solution (the one you started with).

			Similarly, if the mode is symmetric but the puzzle isn't required to be strictly
			minimal, then you can do the same by emptying pairs of cells until there's more than
			one solution; at which point, put the last pair back and deliver the puzzle. It may
			potentially have one more given than strictly required but it will have only one
			solution.

			But symmetric puzzles that must be strictly minimal face a difficulty. Suppose you
			have removed some pairs already and the puzzle still only has one solution (the one
			you started with). But when you remove the next pair, the number of solutions
			increases above one (i.e., the puzzle is now ambiguous). If you put the pair you had
			experimentally removed back, although you will return to having only one solution,
			it may be that only one of the givens in the pair was required; in which case, it
			has one unique solution but it's not minimal. It may have one given that's not
			stricly needed to solve the puzzle.

			In some combinations of the initially randomly generated solution and the particular
			symmetry mode, there may not exist any proper puzzle. In that case, generate another
			solution and try again. */

        while (true) {
            val out = attempt(symmetry)

            if (!minimal || Solver.isMinimal(out))
                return out
        }
    }

    private fun attempt(symmetry: Symmetry): Grid {
        assert(symmetry != Symmetry.RANDOM)

        val solution = cached       // the solution that was used to validate the constructor argument
        cached = null               // can be used one time only

        var out = if (solution != null)
            solution
        else {
            val solutions = Solver.findSolutions(prototype, 1)
            assert(solutions.size == 1)
            solutions[0]
        }

        /*	generate indices into the grid's cells and shuffle their order. The cells of the
		  	grid will be traversed in this order looking for givens that can be removed.
		  	Traversing them in this random order means that the generated puzzles will have
			randomly placed spaces and aren't "top heavy" */

        val remap = IntArray(out.numCells())
        for (i in remap.indices) remap[i] = i + 1    // 1..numCells()
        Util.shuffle(remap)

        /*	start out with a complete solution with all cells filled and iteratively test
			which pairs of cells may be emptied while keeping the puzzle to only one solution */

        for (i in remap.indices) {
            // the two candidate cells to be emptied
            val a = remap[i]
            val b = symmetricCell(a, symmetry)

            //  if a maps to b then b maps to a. Only need to process each pair once
            if (a > b) continue

            val trial = out.withCellEmpty(a).withCellEmpty(b)
            val uniqueSolution = Solver.countSolutions(trial, 2) == 1

            /*	if there's still a unique solution having removed that pair of cells, proceed
			 	with the modified grid as the base (and try other pairs too) */
            if (uniqueSolution) out = trial
        }

        return out
    }

    private fun symmetricCell(nth: Int, symmetry: Symmetry): Int {
        var col = prototype.toColumn(nth)
        var row = prototype.toRow(nth)

        when (symmetry) {
            Symmetry.ROTATE180 -> return prototype.numCells() + 1 - nth
            Symmetry.DIAGONAL -> { val temp = col; col = row; row = temp }
            Symmetry.HORIZONTAL -> row = prototype.numRows() + 1 - row
            Symmetry.VERTICAL -> col = prototype.numColumns() + 1 - col
            Symmetry.RANDOM -> assert(false) { "symmetry not derandomised" }
            Symmetry.NONE -> return nth
        }

        return prototype.toNth(col, row)
    }

    companion object {
        private fun derandomise(symmetry: Symmetry): Symmetry {
            if (symmetry != Symmetry.RANDOM) return symmetry
            val values = Symmetry.values()
            return values[random.nextInt(values.size - 2)]        // exclude RANDOM & NONE
        }

        internal val random = Random(System.currentTimeMillis())
    }
}