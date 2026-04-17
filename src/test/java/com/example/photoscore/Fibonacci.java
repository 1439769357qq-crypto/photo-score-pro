package com.example.photoscore;

public class Fibonacci {
    public static int fib(int n) {
        if (n == 0) return 0; // 特例处理
        if (n == 1) return 1;

        // 只维护当前值和前两个值
        int prev1 = 0; // dp[i-2]
        int prev2 = 1; // dp[i-1]
        int current = 0;

        // 从第 2 项开始推导
        for (int i = 2; i <= n; i++) {
            current = prev1 + prev2; // 当前数 = 前两项之和
            prev1 = prev2; // 更新 dp[i-2]
            prev2 = current; // 更新 dp[i-1]
        }

        return current; // 返回第 n 个斐波那契数
    }

    public static void main(String[] args) {
        System.out.println(fib(6)); // 输出 8
    }
}
