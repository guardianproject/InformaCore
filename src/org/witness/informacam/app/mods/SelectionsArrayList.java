package org.witness.informacam.app.mods;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;

import org.witness.informacam.utils.Constants.App;

import android.util.Log;

public class SelectionsArrayList extends Observable implements List<Selections> {
	List<Selections> selections;
	
	@Override
	public void setChanged() {
		super.setChanged();
		Log.d(App.LOG, "set as changed");
	}
	
	@Override
	public void notifyObservers() {
		super.notifyObservers();
		Log.d(App.LOG, "and notified observers");
	}
	
	public SelectionsArrayList(ArrayList<Selections> selections) {
		this.selections = selections;
	}
	
	private SelectionsArrayList() {
		this.selections = new ArrayList<Selections>();
	}
	
	public static synchronized SelectionsArrayList getInstance() {
        if (instance == null)
            instance = new SelectionsArrayList();
        return instance;
    }

    private static SelectionsArrayList instance;
	
	@Override
	public void add(int location, Selections object) {
		selections.add(location, object);
		setChanged();
		notifyObservers();
		
	}

	@Override
	public boolean addAll(Collection<? extends Selections> arg0) {
		boolean addAll = selections.addAll(arg0);
		if(addAll)
			setChanged();
		else
			Log.e(App.LOG, "uh oh, i couldn't add");
		notifyObservers();
		return addAll;
	}

	@Override
	public boolean addAll(int arg0, Collection<? extends Selections> arg1) {
		boolean addAll = selections.addAll(arg0, arg1);
		if(addAll)
			setChanged();
		notifyObservers();
		return addAll;
	}

	@Override
	public void clear() {
		selections.clear();
		setChanged();
		notifyObservers();
	}

	@Override
	public boolean contains(Object object) {
		return selections.contains(object);
	}

	@Override
	public boolean containsAll(Collection<?> arg0) {
		return selections.containsAll(arg0);
	}

	@Override
	public Selections get(int location) {
		return selections.get(location);
	}

	@Override
	public int indexOf(Object object) {
		return selections.indexOf(object);
	}

	@Override
	public boolean isEmpty() {
		return selections.isEmpty();
	}

	@Override
	public Iterator<Selections> iterator() {
		return selections.iterator();
	}

	@Override
	public int lastIndexOf(Object object) {
		return selections.lastIndexOf(object);
	}

	@Override
	public ListIterator<Selections> listIterator() {
		return selections.listIterator();
	}

	@Override
	public ListIterator<Selections> listIterator(int location) {
		return selections.listIterator(location);
	}

	@Override
	public Selections remove(int location) {
		Selections remove = selections.remove(location);
		setChanged();
		notifyObservers();
		return remove;
	}

	@Override
	public boolean remove(Object object) {
		boolean remove = selections.remove(object);
		if(remove)
			setChanged();
		notifyObservers();
		return remove;
	}

	@Override
	public boolean removeAll(Collection<?> arg0) {
		boolean removeAll = selections.removeAll(arg0);
		if(removeAll)
			setChanged();
		notifyObservers();
		return removeAll;
	}

	@Override
	public boolean retainAll(Collection<?> arg0) {
		boolean retainAll = selections.retainAll(arg0);
		if(retainAll)
			setChanged();
		notifyObservers();
		return retainAll;
	}

	@Override
	public Selections set(int location, Selections object) {
		Selections set = selections.set(location, object);
		if(set !=  null)
			setChanged();
		notifyObservers();
		return set;
	}

	@Override
	public int size() {
		return selections.size();
	}

	@Override
	public List<Selections> subList(int start, int end) {
		return selections.subList(start, end);
	}

	@Override
	public Object[] toArray() {
		return selections.toArray();
	}

	@Override
	public <T> T[] toArray(T[] array) {
		return selections.toArray(array);
	}

	@Override
	public boolean add(Selections object) {
		boolean add = selections.add(object);
		if(add)
			setChanged();
		else
			Log.e(App.LOG, "what the fuck why did this not add?");
		notifyObservers();
		return add;
	}
}
