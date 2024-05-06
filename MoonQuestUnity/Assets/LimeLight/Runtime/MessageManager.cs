using System.Collections.Generic;
using TMPro;
using UnityEngine;

[RequireComponent(typeof(TextMeshProUGUI))]
public class MessageManager : MonoBehaviour
{
	private TextMeshProUGUI mTMP;
	public static MessageManager Instance { get; private set; }

	private Queue<string> messageQueue = new Queue<string>();
	public int maxMessages = 10;
	//MonoLifeCycle-----------
	private void Awake()
	{
		if (Instance != null)
		{
			Destroy(gameObject);
			Debug.LogError("MessageManager already exists in the scene");
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
	/* private void Update() */
	/* { */
	/* 	//Test The MessageManager */
	/* 	if (Input.GetKeyDown(KeyCode.I)) */
	/* 	{ */
	/* 		Info("Info Message"); */
	/* 	} */
	/* 	if (Input.GetKeyDown(KeyCode.W)) */
	/* 	{ */
	/* 		Warning("Warning Message"); */
	/* 	} */
	/* 	if (Input.GetKeyDown(KeyCode.E)) */
	/* 	{ */
	/* 		Error("Error Message"); */
	/* 	} */
	/* } */
	//---------Messages-------------
	public void Info(string msg)
	{
		AddMessage(msg);
	}
	public void Warn(string msg)
	{
		AddMessage("<color=yellow>" + msg + "</color>");
	}
	public void Error(string msg)
	{
		AddMessage("<color=red>" + msg + "</color>");
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
