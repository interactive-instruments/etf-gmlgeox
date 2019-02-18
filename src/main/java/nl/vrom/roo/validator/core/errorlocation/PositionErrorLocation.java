package nl.vrom.roo.validator.core.errorlocation;

public class PositionErrorLocation extends ErrorLocation {

	protected int line;

	protected int column;

	public PositionErrorLocation(int line, int column) {
		this.line = line;
		this.column = column;
	}

	public int getLine() {
		return line;
	}

	public int getColumn() {
		return column;
	}
}
