package net.bigtangle.tools.container;

import java.util.HashSet;

public class BlockHashContainer extends HashSet<String> {

	private static final long serialVersionUID = -6481229326555453595L;

	private static final BlockHashContainer instance = new BlockHashContainer();
	
	public static BlockHashContainer getInstance() {
		return instance;
	}
}
