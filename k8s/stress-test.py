import argparse
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

def send_request(user_id, timeout=10):
    """
    发送单个请求
    """
    url = f"http://localhost:8080/api/seckill/1?userId={user_id}"
    print(f"Starting stress test with user_id: {user_id} \n")
    try:
        req = urllib.request.Request(url, method='POST')
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            return user_id, True, status, None
    except Exception as e:
        return user_id, False, None, str(e)

def main():
    parser = argparse.ArgumentParser(description="Simple HTTP stress test for seckill API")
    parser.add_argument("--userCnt", type=int, required=True, help="Total number of users (userId from 0 to userCnt-1)")
    parser.add_argument("--parallel", type=int, default=10, help="Number of concurrent threads (default: 10)")
    args = parser.parse_args()

    user_cnt = args.userCnt
    parallel = args.parallel

    if user_cnt <= 0 or parallel <= 0:
        print("Error: userCnt and parallel must be positive integers.")
        return

    print(f"Starting stress test with {user_cnt} users, {parallel} parallel threads...\n")

    start_time = time.time()
    success_count = 0
    fail_count = 0

    # 使用线程池并发执行
    with ThreadPoolExecutor(max_workers=parallel) as executor:
        # 提交所有任务
        future_to_user = {
            executor.submit(send_request, user_id): user_id
            for user_id in range(1, user_cnt + 1)   # 生成 1, 2, 3, ..., userCnt
        }

        # 收集结果
        for future in as_completed(future_to_user):
            user_id, success, status, error = future.result()
            if success:
                success_count += 1
                # 可选：打印成功日志（建议关闭，避免刷屏）
                # print(f"[OK] userId={user_id}, status={status}")
            else:
                fail_count += 1
                # 打印失败信息（可选）
                print(f"[FAIL] userId={user_id}: {error}")

    total_time = time.time() - start_time
    print("\n" + "="*50)
    print(f"✅ Total requests: {user_cnt}")
    print(f"✅ Success: {success_count}")
    print(f"❌ Failures: {fail_count}")
    print(f"⏱️  Total time: {total_time:.2f} seconds")
    print(f"📈 Requests per second (RPS): {user_cnt / total_time:.2f}")
    print("="*50)

if __name__ == "__main__":
    main()