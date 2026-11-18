#include <algorithm>
#include <iostream>
#include <vector>

int main() {
    std::vector<int> numbers{8, 3, 11, 2, 3};
    std::sort(numbers.begin(), numbers.end());
    std::cout << "Hello from mobiCode!\nSorted:";
    for (int number : numbers) std::cout << ' ' << number;
    std::cout << '\n';
}
