package com.example.learning9_abstract;

public interface Code42_human extends Code39_creature {
	public abstract void talk();
	public abstract void watch();
	void hear(); // 省略してもいい //
	//さらに親interfaceからrun() 継承する
}
