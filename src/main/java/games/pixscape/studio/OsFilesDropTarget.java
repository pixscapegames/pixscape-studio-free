package games.pixscape.studio;

public interface OsFilesDropTarget {
    /**
     * return true if the drop was consumed
     */
    boolean onOsFilesDropped(String[] files);
}
