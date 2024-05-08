#define DEBUGING
using System.Collections.Generic;
using TMPro;
using UnityEngine;

[RequireComponent(typeof(TextMeshProUGUI))]
public class MessageManager : MonoBehaviour
{
	private TextMeshProUGUI mTMP;
	public static MessageManager Instance { get; private set; }

	private readonly Queue<string> messageQueue = new();
	public int maxMessages = 10;
	//MonoLifeCycle-----------
	private void Awake()
	{
		if (Instance != null)
		{
			Destroy(gameObject);
			UnityEngine.Debug.LogError("MessageManager already exists in the scene");
			return;
		}
		Instance = this;

	}
	private void Start()
	{
		mTMP = GetComponent<TextMeshProUGUI>();
	}

	private void OnDestroy()
	{
		if (Instance == this)
		{
			Instance = null;
		}
	}
	//---------Messages-------------
	public static void Info(string msg)
	{
		Instance.AddMessage(msg);
	}
	public static void Warn(string msg)
	{
		Instance.AddMessage("<color=yellow>" + msg + "</color>");
	}
	public static void Error(string msg)
	{
		Instance.AddMessage("<color=red>" + msg + "</color>");
	}
	public static void Debug(string msg)
	{
#if DEBUGING
		Instance.AddMessage("<color=green>" + msg + "</color>");
#endif
	}
	private void AddMessage(string msg)
	{
		messageQueue.Enqueue(msg);
		if (messageQueue.Count > maxMessages)
		{
			messageQueue.Dequeue();
		}
		UpdateText();
	}
	//---------Text-------------
	private void UpdateText()
	{
		mTMP.text = string.Join("\n", messageQueue.ToArray());
	}
}
