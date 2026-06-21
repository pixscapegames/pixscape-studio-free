package games.pixscape.studio.ui.main;

public record Resolution(
        int witdht,
        int height

) {
    @Override
    public String toString() {
        return witdht + " X " + height;
    }
}
