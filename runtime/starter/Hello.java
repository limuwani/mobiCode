import java.util.Arrays;

public class Hello {
    public static void main(String[] args) {
        int[] numbers = {8, 3, 11, 2, 3};
        Arrays.sort(numbers);
        System.out.println("Hello from mobiCode!");
        System.out.println("Sorted: " + Arrays.toString(numbers));
    }
}
