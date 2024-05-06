
using System.Collections;
using UnityEngine;
public class CoroutineRunner : MonoBehaviour
{
	private static CoroutineRunner instance;
	private static GameObject coroutineRunnerObject;
	private void Awake()
	{
		if (instance == null)
		{
			instance = this;
			DontDestroyOnLoad(gameObject);
		}
		else
		{
			Destroy(gameObject);
		}
	}

	public static Coroutine Start(IEnumerator coroutine)
	{
		if (instance == null)
		{
			if (coroutineRunnerObject == null)
			{
				coroutineRunnerObject = new GameObject("CoroutineRunner");
			}
			instance = coroutineRunnerObject.AddComponent<CoroutineRunner>();
		}
		return instance.StartCoroutine(coroutine);
	}

	public static void Stop(Coroutine coroutine)
	{
		if (instance != null && coroutine != null)
		{
			instance.StopCoroutine(coroutine);
		}
	}
}
